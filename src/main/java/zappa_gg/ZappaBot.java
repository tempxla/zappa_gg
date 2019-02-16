package zappa_gg;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import services.TwitterService;
import twitter4j.PagableResponseList;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Servlet implementation class ZappaBot
 */
@SuppressWarnings("serial")
@WebServlet("/cron/ZappaBot")
public final class ZappaBot extends HttpServlet {

  public static final String SCREEN_NAME = "zappa_gg";

  private static final Logger logger = Logger.getLogger(ZappaBot.class.getName());

  private static final String TIMEZONE = "Asia/Tokyo";
  private static final int PER_MINUTES_CRON = 6;

  private static final String SPEAK_LIST_NAME = "speak";
  private static final int API_PAGE_SIZE = 100;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    run(request, response);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    run(request, response);
  }

  /**
   * メイン
   *
   * @param request
   * @param response
   */
  private void run(HttpServletRequest request, HttpServletResponse response) {
    final Twitter tw = new TwitterService().makeTwitterObject(SCREEN_NAME);
    try {
      // CRON 6min の場合 1/10 の確率でツイートする。大体1時間に1回ツイート。
      if (probably(1, 60 / PER_MINUTES_CRON)) {
        tw.updateStatus(selectStringRandom(Messages.TWEETS));
      }
      // リプライ済みのアカウント
      final Set<String> replied = new HashSet<>();
      // 2/3 の確率で返信する。
      if (probably(2, 3)) {
        reply(tw, replied);
      }
      // 3日に1回くらい、誰かに話かける。
      if (probably(1, 60 * 24 * 3 / PER_MINUTES_CRON)) {
        speak(tw, replied);
      }
    } catch (TwitterException e) {
      logger.warning(e.toString());
    }
  }

  /**
   * 返信する
   *
   * @param tw
   * @param replied
   * @throws TwitterException
   */
  private void reply(Twitter tw, Set<String> replied) throws TwitterException {
    // 前回のCRON実行日時
    final Calendar tmp = Calendar.getInstance(TimeZone.getTimeZone(TIMEZONE));
    tmp.add(Calendar.MINUTE, -PER_MINUTES_CRON);
    final Date prevCronRunAt = tmp.getTime();
    for (Status s : tw.getMentionsTimeline()) {
      // 前回のCRON実行日時からの差分
      if (s.getCreatedAt().after(prevCronRunAt)) {
        final String screenName = s.getUser().getScreenName();
        if (!replied.contains(screenName)) {
          final String tweet = makeReplyFormat(screenName, selectStringRandom(Messages.TWEETS));
          tw.updateStatus(new StatusUpdate(tweet).inReplyToStatusId(s.getId()));
          replied.add(screenName);
        }
      }
    }
  }

  /**
   * 話かける
   *
   * @param tw
   * @param replied
   * @throws TwitterException
   */
  private void speak(Twitter tw, Set<String> replied) throws TwitterException {
    final Set<String> speakList = tw
        .getUserListMembers(ZappaBot.SCREEN_NAME, SPEAK_LIST_NAME, API_PAGE_SIZE, PagableResponseList.START, true)
        .stream().map(User::getScreenName).filter(s -> !replied.contains(s)).collect(Collectors.toSet());
    if (!speakList.isEmpty()) {
      final String screenName = selectStringRandom(speakList.toArray(new String[speakList.size()]));
      tw.updateStatus(makeReplyFormat(screenName, selectStringRandom(Messages.TWEETS)));
      replied.add(screenName);
    }
  }

  /**
   * リプライのフォーマット
   *
   * @param screenName
   * @param text
   * @return
   */
  private String makeReplyFormat(String screenName, String text) {
    return "@" + screenName + " " + text;
  }

  /**
   * 一定の確率でtrueを返す
   *
   * @param numerator
   *          分子
   * @param denominator
   *          分母
   * @return
   */
  private boolean probably(int numerator, int denominator) {
    return numerator > makeRandomValue(denominator);
  }

  /**
   * 配列の中からランダムに一つ選ぶ。
   *
   * @param stringList
   * @return
   */
  private String selectStringRandom(String[] stringList) {
    return stringList[makeRandomValue(stringList.length)];
  }

  /**
   * 0〜size-1 のランダムな整数を返す。3の場合、0 or 1 or 2。
   *
   * @param size
   * @return
   */
  private int makeRandomValue(int size) {
    return (int) (Math.random() * size);
  }

}
