package services;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Date;
import java.util.HashMap;
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
import twitter4j.ResponseList;
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
  private static final int GAE_PAGE_SIZE = 500;
  private static final int REMOVE_DAYS = 30;

  // GAE の1日あたりの制限 エンティティ 読み込み数 50,000 書き込み数 20,000 削除数 20,000
  // 1回の実行で、Twitter: 100 PageSize * 8 Count = 800 ( > DataStore: 500 )
  // Entity 書き込み 2時間毎に実行した場合、1日で 800 * 12 = 9,600
  // 1日あたりの制限の半分以下くらいに収まる。
  // よって2時間毎に実行することとする。

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
    final int limit = Math.min(rateLimitStatus.getRemaining(), API_LIST_MAX_COUNT);
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
   * @param date
   * @return true: 完了. false: 残あり.
   * @throws TwitterException
   */
  public boolean updateFollowerDate(Twitter tw, Date date) throws TwitterException {
    final TwitterFriendDao dao = new TwitterFriendDao();
    // フォロワー
    long cur = loadList(tw, LIMIT_STATUS_FOLLOWERS, API_FOLLOWERS_LIST, (Long cursor) -> {
      PagableResponseList<User> users = tw.getFollowersList(ZappaBot.SCREEN_NAME, cursor, API_LIST_PAGE_SIZE, true,
          false);
      users.stream().forEach(user -> dao.saveLastFollowedByDate(user.getId(), date));
      return users.getNextCursor();
    });
    return cur == 0;
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
    final TwitterFriendDao dao = new TwitterFriendDao();
    // フォロイー
    long cur = loadList(tw, LIMIT_STATUS_FRIENDS, API_FRIENDS_LIST, (Long cursor) -> {
      PagableResponseList<User> users = tw.getFriendsList(ZappaBot.SCREEN_NAME, cursor, API_LIST_PAGE_SIZE, true,
          false);
      users.stream().forEach(user -> dao.saveLastFollowingDate(user.getId(), date));
      return users.getNextCursor();
    });
    return cur == 0;
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
   * DBから取得したステータスを元に、フォロー/リムーブを実行する。
   *
   * @param tw
   * @param date
   * @return true: 完了. false: 残あり.
   * @throws TwitterException
   */
  public boolean updateFriendships(Twitter tw, Date date) throws TwitterException {
    // ログ用
    int logRemoveCount = 0;
    int logFollowCount = 0;
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
    // リムーブ対象外リスト
    Set<Long> followList = tw
        .getUserListMembers(ZappaBot.SCREEN_NAME, FOLLOW_LIST_NAME, API_LIST_PAGE_SIZE, PagableResponseList.START, true)
        .stream().map(User::getId).collect(Collectors.toSet());
    // Twitterステータス更新用
    TwitterFriendDao twitterFriendDao = new TwitterFriendDao();
    // DBから取得。Twitterの情報を更新。
    QueryResultIterator<TwitterFriend> iter = query.iterator();
    boolean hasNext = false; // 次ページがあるか
    String newCursor = null;
    try {
      while (iter.hasNext()) {
        hasNext = true;
        TwitterFriend user = iter.next();
        try {
          if (user.getLastFollowedByDate() != null && user.getLastFollowingDate() == null) {
            // フォローされている & フォローしてない場合、フォロー
            // ただし、アンフォロー判定を考慮する
            if (!user.isUnfollow()) {
              tw.createFriendship(user.getId());
              logFollowCount++;
            }
          } else if ((user.getLastFollowedByDate() == null && user.getLastFollowingDate() != null)
              || (user.getLastFollowedByDate() != null
                  && DateUtil.addDays(user.getLastFollowedByDate(), REMOVE_DAYS).compareTo(date) < 0)) {
            // フォローされていない & フォローしている場合、リムーブ
            // 一定期間フォローされていない場合、リムーブ
            // ただし、followリストに含まれる場合は対象外
            if (!followList.contains(user.getId())) {
              tw.destroyFriendship(user.getId());
              twitterFriendDao.delete(user.getId());
              logRemoveCount++;
            }
          } else if (user.getLastFollowingDate() != null
              && DateUtil.addDays(user.getLastFollowingDate(), REMOVE_DAYS).compareTo(date) < 0) {
            // 一定期間フォローしていない場合、DBから削除
            // ただし、アンフォロー判定を考慮する
            if (!user.isUnfollow()) {
              twitterFriendDao.delete(user.getId());
            }
          }
          // フォロー判定
          if (user.getLastFollowingDate() != null && user.isUnfollow() && !followList.contains(user.getId())) {
            // フォローしている & アンフォロー判定 & フォローリストにない場合、リムーブを実行。
            tw.destroyFriendship(user.getId());
            twitterFriendDao.unfollow(user);
            logRemoveCount++;
          }
        } catch (TwitterException e) {
          // ユーザーが見つからない場合、DBから削除
          // statusCode=403, message=Cannot find specified user., code=108
          if (e.getErrorCode() == 108) {
            twitterFriendDao.delete(user.getId());
          } else {
            LogUtil.sendDirectMessage(tw, String.format("%s: user:%d statusCode:%d code:%d message:%s",
                Messages.ERROR_MESSAGE, user.getId(), e.getStatusCode(), e.getErrorCode(), e.getMessage()));
            throw e;
          }
        }
      }
    } finally {
      // カーソル位置を保存
      if (hasNext) {
        newCursor = iter.getCursor().toWebSafeString();
      }
      if (nextCursor == null) {
        nextCursorDao.insertNextCursor(GAE_FRIEND_LIST, newCursor);
      } else {
        nextCursorDao.updateNextCursor(nextCursor, newCursor);
      }
    }
    logger.info(String.format("follow:%d remove:%d", logFollowCount, logRemoveCount));
    return newCursor == null || newCursor.isEmpty();
  }

  /**
   * ツイッターからユーザー情報を取得し、フォロースべきか判定結果をDBに書き込む。
   *
   * @param tw
   * @return true: 完了. false: 残あり.
   * @throws TwitterException
   */
  public boolean updateUnfollow(Twitter tw) throws TwitterException {
    // ログ用
    int logUnfollowCount = 0;
    // Function
    TwitterFriendDao twitterFriendDao = new TwitterFriendDao();
    UnfollowUtil unfollowUtil = new UnfollowUtil();
    Function<Map<Long, TwitterFriend>, Integer> detectUnfollow = friends -> {
      int cnt = 0;
      try {
        ResponseList<User> users = tw.lookupUsers(friends.keySet().stream().mapToLong(Long::longValue).toArray());
        for (User user : users) {
          if (unfollowUtil.detectUnfollow(user)) {
            twitterFriendDao.updateUnfollow(friends.get(user.getId()), true);
            cnt++;
          }
        }
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
      return cnt;
    };
    // カーソル初期化
    Query<TwitterFriend> query = ofy().load().type(TwitterFriend.class).limit(GAE_PAGE_SIZE);
    NextCursorDao nextCursorDao = new NextCursorDao();
    NextCursor nextCursor = nextCursorDao.loadById(GAE_UNFOLLOW_LIST);
    String cursor = null;
    if (nextCursor != null) {
      cursor = nextCursor.getNextCursor();
      if (cursor != null) {
        query = query.startAt(Cursor.fromWebSafeString(nextCursor.getNextCursor()));
      }
    }
    // DBから取得。
    QueryResultIterator<TwitterFriend> iter = query.iterator();
    boolean hasNext = false; // 次ページがあるか
    String newCursor = null;
    try {
      Map<Long, TwitterFriend> friends = new HashMap<>();
      while (iter.hasNext()) {
        hasNext = true;
        TwitterFriend friend = iter.next();
        if (friend.isUnfollow()) {
          continue;
        }
        friends.put(friend.getId(), friend);
        if (friends.size() == API_LOOKUP_PAGE_SIZE) {
          logUnfollowCount += detectUnfollow.apply(friends);
          friends.clear();
        }
      }
      if (0 < friends.size() && friends.size() < API_LOOKUP_PAGE_SIZE) {
        logUnfollowCount += detectUnfollow.apply(friends);
      }
    } finally {
      // カーソル位置を保存
      if (hasNext) {
        newCursor = iter.getCursor().toWebSafeString();
      }
      if (nextCursor == null) {
        nextCursorDao.insertNextCursor(GAE_UNFOLLOW_LIST, newCursor);
      } else {
        nextCursorDao.updateNextCursor(nextCursor, newCursor);
      }
    }
    logger.info(String.format("unfollow:%d", logUnfollowCount));
    return newCursor == null || newCursor.isEmpty();
  }

}
