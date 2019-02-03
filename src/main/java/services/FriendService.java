package services;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.googlecode.objectify.cmd.Query;

import daos.NextCursorDao;
import daos.TaskDao;
import daos.TwitterFriendDao;
import entities.NextCursor;
import entities.Task;
import entities.TwitterFriend;
import twitter4j.PagableResponseList;
import twitter4j.RateLimitStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import util.DateUtil;
import zappa_gg.ZappaBot;

public class FriendService {

  private static final String GAE_FRIEND_LIST = "/gae/datastore/TwitterFriend";
  private static final String API_FOLLOWERS_LIST = "/followers/list";
  private static final String API_FRIENDS_LIST = "/friends/list";
  private static final String LIMIT_STATUS_FOLLOWERS = "followers";
  private static final String LIMIT_STATUS_FRIENDS = "friends";
  private static final int API_PAGE_SIZE = 100;
  private static final int API_MAX_COUNT = 15;
  private static final int GAE_PAGE_SIZE = 1000;
  private static final int REMOVE_DAY = 30;

  // GAE の1日あたりの制限 エンティティ 読み込み数 50,000 書き込み数 20,000 削除数 20,000
  // 1回の実行で、Twitter: 100 PageSize * 15 Count = 1,500 ( > DataStore: 1000 )
  // Entity 書き込み 4時間毎に実行した場合、1日で 1,500 * 6 = 9,000
  // 1日あたりの制限の半分以下くらいに収まる。
  // よって4時間毎に実行することとする。

  @FunctionalInterface
  private interface Function<T, R> {
    R apply(T t) throws TwitterException;
  }

  /**
   * フォローリスト・フォロワーリスト共通処理
   *
   * @param tw
   * @param statusName
   * @param apiName
   * @param listFunc
   * @return
   * @throws TwitterException
   */
  private long loadList(Twitter tw, String statusName, String apiName, Function<Long, Long> listFunc)
      throws TwitterException {
    // API制限取得
    final RateLimitStatus rateLimitStatus = tw.getRateLimitStatus(statusName).get(apiName);
    final int limit = Math.min(rateLimitStatus.getRemaining(), API_MAX_COUNT);
    // カーソル初期化
    long cursor = PagableResponseList.START;
    NextCursorDao nextCursorDao = new NextCursorDao();
    NextCursor nextCursor = nextCursorDao.loadById(apiName);
    if (nextCursor != null && nextCursor.getNextCursor() != null && !nextCursor.getNextCursor().equals("0")) {
      cursor = Long.valueOf(nextCursor.getNextCursor());
    }
    // Twitter読み込み & DB書き込み
    try {
      for (int i = 0; i < limit && cursor != 0; i++) {
        cursor = listFunc.apply(cursor);
      }
    } finally {
      // カーソル位置を保存
      if (nextCursor == null) {
        nextCursorDao.insertNextCursor(apiName, String.valueOf(cursor));
      } else {
        nextCursorDao.updateNextCursor(nextCursor, String.valueOf(cursor));
      }
    }
    return cursor;
  }

  /**
   * ツイッターのフォロワーのリストを取得し、取得日時と共にDBに書き込む。
   *
   * @param tw
   * @throws TwitterException
   */
  public void updateFollowerDate(Twitter tw) throws TwitterException {
    final Date nowDate = new Date();
    final TwitterFriendDao dao = new TwitterFriendDao();
    // フォロワー
    long cur = loadList(tw, LIMIT_STATUS_FOLLOWERS, API_FOLLOWERS_LIST, (Long cursor) -> {
      PagableResponseList<User> users = tw.getFollowersList(ZappaBot.SCREEN_NAME, cursor, API_PAGE_SIZE, true, false);
      users.stream().forEach(user -> dao.saveLastFollowedByDate(user.getId(), nowDate));
      return users.getNextCursor();
    });
    // タスクの状態を更新
    new TaskDao().updateTask(TaskDao.TASK_TW_FOLLOWERS_LIST, cur == 0 ? Task.WAIT : Task.RUNNING, nowDate);
  }

  /**
   * ツイッターのフォローのリストを取得し、取得日時と共にDBに書き込む。
   *
   * @param tw
   * @throws TwitterException
   */
  public void updateFollowingDate(Twitter tw) throws TwitterException {
    final Date nowDate = new Date();
    final TwitterFriendDao dao = new TwitterFriendDao();
    // フォロイー
    long cur = loadList(tw, LIMIT_STATUS_FRIENDS, API_FRIENDS_LIST, (Long cursor) -> {
      PagableResponseList<User> users = tw.getFriendsList(ZappaBot.SCREEN_NAME, cursor, API_PAGE_SIZE, true, false);
      users.stream().forEach(user -> dao.saveLastFollowingDate(user.getId(), nowDate));
      return users.getNextCursor();
    });
    // タスクの状態を更新
    new TaskDao().updateTask(TaskDao.TASK_TW_FRIENDS_LIST, cur == 0 ? Task.WAIT : Task.RUNNING, nowDate);
  }

  /**
   * ツイッターAPI制限ステータス取得
   *
   * @param tw
   * @return
   * @throws TwitterException
   */
  public Map<String, RateLimitStatus> loadRateLimitStatus(Twitter tw) throws TwitterException {
    return tw.getRateLimitStatus(LIMIT_STATUS_FOLLOWERS, LIMIT_STATUS_FRIENDS).entrySet().stream()
        .filter(e -> e.getKey().equals(API_FOLLOWERS_LIST) || e.getKey().equals(API_FRIENDS_LIST))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * DBから取得したステータスを元に、フォロー/リムーブを実行する。
   *
   * @param tw
   * @throws TwitterException
   */
  public void updateFriendships(Twitter tw) throws TwitterException {
    final Date nowDate = new Date();
    // カーソル初期化
    Query<TwitterFriend> query = ofy().load().type(TwitterFriend.class).limit(GAE_PAGE_SIZE);
    NextCursorDao nextCursorDao = new NextCursorDao();
    NextCursor nextCursor = nextCursorDao.loadById(GAE_FRIEND_LIST);
    String cursor = null;
    if (nextCursor != null) {
      cursor = nextCursor.getNextCursor();
      if (cursor != null) {
        query = query.startAt(Cursor.fromWebSafeString(nextCursor.getNextCursor()));
      }
    }
    // Twitterステータス更新用
    TwitterFriendDao twitterFriendDao = new TwitterFriendDao();
    // DBから取得。Twitterの情報を更新。
    QueryResultIterator<TwitterFriend> iter = query.iterator();
    boolean hasNext = false; // 次ページがあるか
    String newCursor = null;
    try {
      while (iter.hasNext()) {
        TwitterFriend user = iter.next();
        try {
          if (user.getLastFollowedByDate() != null && user.getLastFollowingDate() == null) {
            // フォローされている & フォローしてない場合、フォロー
            tw.createFriendship(user.getId());
          } else if (user.getLastFollowedByDate() == null && user.getLastFollowingDate() != null) {
            // フォローされていない & フォローしている場合、リムーブ
            tw.destroyFriendship(user.getId());
            twitterFriendDao.delete(user.getId());
          } else if (user.getLastFollowedByDate() != null
              && DateUtil.addDays(user.getLastFollowedByDate(), REMOVE_DAY).compareTo(nowDate) < 0) {
            // 一定期間フォローされていない場合、リムーブ
            tw.destroyFriendship(user.getId());
            twitterFriendDao.delete(user.getId());
          } else if (user.getLastFollowingDate() != null
              && DateUtil.addDays(user.getLastFollowingDate(), REMOVE_DAY).compareTo(nowDate) < 0) {
            // 一定期間フォローしていない場合、DBから削除
            twitterFriendDao.delete(user.getId());
          }
        } catch (TwitterException e) {
          // ユーザーが見つからない場合は無視
          // statusCode=403, message=Cannot find specified user., code=108
          if (e.getErrorCode() != 108) {
            throw e;
          }
        }
        hasNext = true;
      }
    } finally {
      // カーソル位置を保存
      newCursor = null;
      if (hasNext) {
        newCursor = iter.getCursor().toWebSafeString();
      }
      if (nextCursor == null) {
        nextCursorDao.insertNextCursor(GAE_FRIEND_LIST, newCursor);
      } else {
        nextCursorDao.updateNextCursor(nextCursor, newCursor);
      }
    }
    // タスク状態更新
    TaskDao taskDao = new TaskDao();
    int status = newCursor == null || newCursor.isEmpty() ? Task.WAIT : Task.RUNNING;
    taskDao.updateTask(TaskDao.TASK_GAE_FRIEND_LIST, status, nowDate);
  }

}
