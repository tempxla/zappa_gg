package services;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.googlecode.objectify.cmd.Query;

import daos.NextCursorDao;
import daos.TwitterFriendDao;
import entities.NextCursor;
import entities.TwitterFriend;
import twitter4j.PagableResponseList;
import twitter4j.RateLimitStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import util.DateUtil;
import util.LogUtil;
import util.UnfollowUtil;
import zappa_gg.Messages;
import zappa_gg.ZappaBot;

public class FriendService {

  private static final Logger logger = Logger.getLogger(FriendService.class.getName());

  private static final String GAE_FRIEND_LIST = "/gae/datastore/TwitterFriend";
  private static final String GAE_UNFOLLOW_LIST = "/gae/datastore/Unfollow";
  private static final String API_FOLLOWERS_LIST = "/followers/list";
  private static final String API_FRIENDS_LIST = "/friends/list";
  private static final String API_USERS_LIST = "/users/lookup";
  private static final String LIMIT_STATUS_FOLLOWERS = "followers";
  private static final String LIMIT_STATUS_FRIENDS = "friends";
  private static final String LIMIT_STATUS_USERS = "users";
  private static final String FOLLOW_LIST_NAME = "follow";
  private static final int API_LIST_PAGE_SIZE = 100;
  private static final int API_LOOKUP_PAGE_SIZE = 100;
  private static final int API_LIST_MAX_COUNT = 8;
  private static final int GAE_PAGE_SIZE = 800;
  private static final int REMOVE_DAYS = 30;

  // GAE の1日あたりの制限 エンティティ 読み込み数 50,000 書き込み数 20,000 削除数 20,000
  // 1回の実行で、Twitter: 100 PageSize * 8 Count = 800 ( DataStore: 800 )
  // Entity 書き込み 2時間毎に実行した場合、1日で 800 * 12 = 9,600
  // 1日あたりの制限の半分以下くらいに収まる。

  @FunctionalInterface
  private interface Function<T, R> {
    R apply(T t) throws TwitterException;
  }

  @FunctionalInterface
  private interface Consumer<T> {
    void accept(T t) throws TwitterException;
  }

  @FunctionalInterface
  private interface Runnable {
    void run() throws TwitterException;
  }

  /**
   * フォローリスト・フォロワーリスト共通処理
   *
   * @param tw
   * @param statusName
   * @param apiName
   * @param readFunc
   * @return
   * @throws TwitterException
   */
  private boolean loadList(Twitter tw, String statusName, String apiName, Function<Long, Long> listFunc,
      Runnable saveProc) throws TwitterException {
    // API制限取得
    final RateLimitStatus rateLimitStatus = tw.getRateLimitStatus(statusName).get(apiName);
    final int limit = Math.min(rateLimitStatus.getRemaining(), API_LIST_MAX_COUNT);
    // カーソル初期化
    long cursor = PagableResponseList.START;
    final NextCursorDao nextCursorDao = new NextCursorDao();
    final NextCursor nextCursor = nextCursorDao.loadById(apiName);
    if (nextCursor != null && nextCursor.getNextCursor() != null && !nextCursor.getNextCursor().equals("0")) {
      cursor = Long.valueOf(nextCursor.getNextCursor());
    }
    // Twitter読み込み & DB書き込み
    try {
      for (int i = 0; i < limit && cursor != 0; i++) {
        cursor = listFunc.apply(cursor);
      }
      saveProc.run();
    } finally {
      // カーソル位置を保存
      if (nextCursor == null) {
        nextCursorDao.insertNextCursor(apiName, String.valueOf(cursor));
      } else {
        nextCursorDao.updateNextCursor(nextCursor, String.valueOf(cursor));
      }
    }
    return cursor == 0;
  }

  /**
   * ツイッターのフォロワーのリストを取得し、取得日時と共にDBに書き込む。
   *
   * @param tw
   * @param date
   * @return true: 完了. false: 残あり.
   * @throws TwitterException
   */
  public boolean updateFollowerDate(Twitter tw, Date date) throws TwitterException {
    final TwitterFriendDao twitterFriendDao = new TwitterFriendDao();
    final List<Long> updateList = new ArrayList<>();
    final Function<Long, Long> listFunc = cursor -> {
      PagableResponseList<User> users = tw.getFollowersList(ZappaBot.SCREEN_NAME, cursor, API_LIST_PAGE_SIZE, true,
          false);
      users.stream().forEach(user -> updateList.add(user.getId()));
      return users.getNextCursor();
    };
    final Runnable saveFunc = () -> twitterFriendDao.saveLastFollowedByDate(updateList, date);
    return loadList(tw, LIMIT_STATUS_FOLLOWERS, API_FOLLOWERS_LIST, listFunc, saveFunc);
  }

  /**
   * ツイッターのフォローのリストを取得し、取得日時と共にDBに書き込む。
   *
   * @param tw
   * @param date
   * @return true: 完了. false: 残あり.
   * @throws TwitterException
   */
  public boolean updateFollowingDate(Twitter tw, Date date) throws TwitterException {
    final TwitterFriendDao twitterFriendDao = new TwitterFriendDao();
    final List<Long> updateList = new ArrayList<>();
    final Function<Long, Long> listFunc = cursor -> {
      PagableResponseList<User> users = tw.getFriendsList(ZappaBot.SCREEN_NAME, cursor, API_LIST_PAGE_SIZE, true,
          false);
      users.stream().forEach(user -> updateList.add(user.getId()));
      return users.getNextCursor();
    };
    final Runnable saveFunc = () -> twitterFriendDao.saveLastFollowingDate(updateList, date);
    return loadList(tw, LIMIT_STATUS_FRIENDS, API_FRIENDS_LIST, listFunc, saveFunc);
  }

  /**
   * ツイッターAPI制限ステータス取得
   *
   * @param tw
   * @return
   * @throws TwitterException
   */
  public Map<String, RateLimitStatus> loadRateLimitStatus(Twitter tw) throws TwitterException {
    return tw.getRateLimitStatus(LIMIT_STATUS_FOLLOWERS, LIMIT_STATUS_FRIENDS, LIMIT_STATUS_USERS).entrySet().stream()
        .filter(e -> e.getKey().equals(API_FOLLOWERS_LIST) || e.getKey().equals(API_FRIENDS_LIST)
            || e.getKey().equals(API_USERS_LIST))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * DataStore Query 共通処理
   *
   * @param cursorName
   * @param query
   * @param iterProc
   * @param postProc
   * @return
   * @throws TwitterException
   */
  private <T> boolean runQueryIterator(String cursorName, Query<T> query, Consumer<T> iterProc, Runnable postProc)
      throws TwitterException {
    // カーソル初期化
    final NextCursorDao nextCursorDao = new NextCursorDao();
    final NextCursor nextCursor = nextCursorDao.loadById(cursorName);
    String cursor = null;
    if (nextCursor != null) {
      cursor = nextCursor.getNextCursor();
      if (cursor != null) {
        query = query.startAt(Cursor.fromWebSafeString(cursor));
      }
    }
    // DBから取得。
    final QueryResultIterator<T> iter = query.iterator();
    boolean hasNext = false; // 1件以上あるか
    String newCursor = null;
    try {
      while (iter.hasNext()) {
        hasNext = true;
        iterProc.accept(iter.next());
      }
      postProc.run();
    } finally {
      // カーソル位置を保存
      if (hasNext) {
        newCursor = iter.getCursor().toWebSafeString();
        if (cursor != null && cursor.equals(newCursor)) {
          newCursor = null;
        }
      }
      if (nextCursor == null) {
        nextCursorDao.insertNextCursor(cursorName, newCursor);
      } else {
        nextCursorDao.updateNextCursor(nextCursor, newCursor);
      }
    }
    return newCursor == null || newCursor.isEmpty();
  }

  /**
   * DBから取得したステータスを元に、フォロー/リムーブを実行する。
   *
   * @param tw
   * @param date
   * @return true: 完了. false: 残あり.
   * @throws TwitterException
   */
  public boolean updateFriendships(Twitter tw, Date date) throws TwitterException {
    // ログ用
    final int[] logFollowCount = new int[] { 0 };
    final int[] logRemoveCount = new int[] { 0 };
    // DB更新用
    final TwitterFriendDao twitterFriendDao = new TwitterFriendDao();
    final List<TwitterFriend> updateList = new ArrayList<>();
    final List<TwitterFriend> deleteList = new ArrayList<>();
    // リムーブ対象外リスト
    final Set<Long> notRemoveList = tw
        .getUserListMembers(ZappaBot.SCREEN_NAME, FOLLOW_LIST_NAME, API_LIST_PAGE_SIZE, PagableResponseList.START, true)
        .stream().map(User::getId).collect(Collectors.toSet());
    // リムーブ期日
    final Date removeDay = DateUtil.addDays(date, -REMOVE_DAYS);
    // クエリ
    final Query<TwitterFriend> query = twitterFriendDao.makeQuery(GAE_PAGE_SIZE);
    // メイン
    final Consumer<TwitterFriend> iterProc = user -> {
      try {
        if (user.getLastFollowedByDate() != null && user.getLastFollowingDate() == null) {
          // フォローされている & フォローしてない場合、フォロー
          // ただし、アンフォロー判定を考慮する
          if (!user.isUnfollow()) {
            tw.createFriendship(user.getId());
            logFollowCount[0]++;
          }
        } else if ((user.getLastFollowedByDate() == null && user.getLastFollowingDate() != null)
            || (user.getLastFollowedByDate() != null && removeDay.after(user.getLastFollowedByDate()))) {
          // フォローされていない & フォローしている場合、リムーブ
          // 一定期間フォローされていない場合、リムーブ
          // ただし、followリストに含まれる場合は対象外
          if (!notRemoveList.contains(user.getId())) {
            tw.destroyFriendship(user.getId());
            logRemoveCount[0]++;
            deleteList.add(user);
          }
        } else if (user.getLastFollowingDate() != null && removeDay.after(user.getLastFollowingDate())) {
          // 一定期間フォローしていない場合、DBから削除
          // ただし、アンフォロー判定を考慮する
          if (!user.isUnfollow()) {
            deleteList.add(user);
          }
        }
        // フォロー判定
        if (user.getLastFollowingDate() != null && user.isUnfollow() && !notRemoveList.contains(user.getId())) {
          // フォローしている & アンフォロー判定 & フォローリストにない場合、リムーブを実行。
          tw.destroyFriendship(user.getId());
          logRemoveCount[0]++;
          user.setLastFollowingDate(null); // フォローしてないことにする
          updateList.add(user);
        }
      } catch (TwitterException e) {
        // ユーザーが見つからない場合、DBから削除
        // statusCode=403, message=Cannot find specified user., code=108
        if (e.getErrorCode() == 108) {
          deleteList.add(user);
        } else {
          LogUtil.sendDirectMessage(tw, String.format("%s: user:%d statusCode:%d code:%d message:%s",
              Messages.ERROR_MESSAGE, user.getId(), e.getStatusCode(), e.getErrorCode(), e.getMessage()));
          throw e;
        }
      }
    };
    // 後処理
    final Runnable postProc = () -> {
      twitterFriendDao.save(updateList);
      twitterFriendDao.delete(deleteList);
      logger.info(String.format("follow:%d remove:%d", logFollowCount[0], logRemoveCount[0]));
    };
    // クエリ実行
    return runQueryIterator(GAE_FRIEND_LIST, query, iterProc, postProc);
  }

  /**
   * ツイッターからユーザー情報を取得し、フォロースべきか判定結果をDBに書き込む。
   *
   * @param tw
   * @return true: 完了. false: 残あり.
   * @throws TwitterException
   */
  public boolean updateUnfollow(Twitter tw) throws TwitterException {
    // DB更新用
    final TwitterFriendDao twitterFriendDao = new TwitterFriendDao();
    final List<TwitterFriend> unfollowList = new ArrayList<>();
    // DBから取得したキャッシュ
    final Map<Long, TwitterFriend> queryCache = new HashMap<>();
    // Twitter APIのパラメタ
    final List<Long> lookupParam = new ArrayList<>();
    // Twitterからユーザー情報取得
    final UnfollowUtil unfollowUtil = new UnfollowUtil();
    final Runnable detectUnfollow = () -> {
      try {
        tw.lookupUsers(lookupParam.stream().mapToLong(Long::longValue).toArray()).stream()
            .filter(unfollowUtil::detectUnfollow).map(User::getId).forEach(id -> {
              TwitterFriend e = queryCache.get(id);
              e.setUnfollow(true);
              unfollowList.add(e);
            });
      } catch (TwitterException e) {
        // statusCode=404, message=No user matches for specified terms., code=17
        if (e.getErrorCode() == 17) {
          // ignore
        } else {
          LogUtil.sendDirectMessage(tw, String.format("%s: Fail unfollow, statusCode:%d code:%d message:%s",
              Messages.ERROR_MESSAGE, e.getStatusCode(), e.getErrorCode(), e.getMessage()));
          throw e;
        }
      }
    };
    // クエリ
    final Query<TwitterFriend> query = twitterFriendDao.makeQuery(GAE_PAGE_SIZE);
    // メイン
    final Consumer<TwitterFriend> iterProc = user -> {
      // 一度アンフォローと推定されたものはスキップ
      // QueryでFilterはうまくいかないかも。。。
      if (!user.isUnfollow()) {
        lookupParam.add(user.getId());
        queryCache.put(user.getId(), user);
        if (lookupParam.size() == API_LOOKUP_PAGE_SIZE) {
          detectUnfollow.run();
          lookupParam.clear();
          queryCache.clear();
        }
      }
    };
    // 後処理
    final Runnable postProc = () -> {
      if (0 < lookupParam.size() && lookupParam.size() < API_LOOKUP_PAGE_SIZE) {
        detectUnfollow.run();
      }
      twitterFriendDao.save(unfollowList);
      logger.info(String.format("unfollow:%d", unfollowList.size()));
    };
    // クエリ実行
    return runQueryIterator(GAE_UNFOLLOW_LIST, query, iterProc, postProc);
  }

}
