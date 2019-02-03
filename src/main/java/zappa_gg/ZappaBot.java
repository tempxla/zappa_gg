package zappa_gg;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import services.TwitterService;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Servlet implementation class ZappaBot
 */
@SuppressWarnings("serial")
@WebServlet("/cron/ZappaBot")
public final class ZappaBot extends HttpServlet {

  public static final String SCREEN_NAME = "zappa_gg";

  private static final String TIMEZONE = "Asia/Tokyo";
  private static final int PER_MINUTES_CRON = 6;

  private static final Logger logger = Logger.getLogger(ZappaBot.class.getName());

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
        tw.updateStatus(selectTweetRandom(Messages.TWEETS));
      }
      // 2/3 の確率で返信する
      if (probably(2, 3)) {
        reply(tw);
      }
    } catch (TwitterException e) {
      logger.log(Level.WARNING, e.toString());
    }
  }

  /**
   * 返信する
   *
   * @param tw
   * @throws TwitterException
   */
  private void reply(Twitter tw) throws TwitterException {
    // 前回のCRON実行日時
    Calendar tmp = Calendar.getInstance(TimeZone.getTimeZone(TIMEZONE));
    tmp.add(Calendar.MINUTE, -PER_MINUTES_CRON);
    Date prevCronRunAt = tmp.getTime();
    for (Status s : tw.getMentionsTimeline()) {
      // 前回のCRON実行日時からの差分
      if (s.getCreatedAt().after(prevCronRunAt)) {
        String tweet = "@" + s.getUser().getScreenName() + " " + selectTweetRandom(Messages.TWEETS);
        tw.updateStatus(new StatusUpdate(tweet).inReplyToStatusId(s.getId()));
      }
    }
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
   * @param tweets
   * @return
   */
  private String selectTweetRandom(String[] tweets) {
    return tweets[makeRandomValue(tweets.length)];
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
