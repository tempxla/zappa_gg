package util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Logger;

import twitter4j.Twitter;
import twitter4j.TwitterException;

public class LogUtil {

  private static final Logger logger = Logger.getLogger(LogUtil.class.getName());

  private static final String SCREEN_NAME = "temp_la";

  public static void sendDirectMessage(Twitter tw, String text) {
    try {
      tw.sendDirectMessage(SCREEN_NAME, text);
    } catch (TwitterException e) {
      logger.severe(e.toString());
    }
  }

  public static String printStackTraceString(Exception e) {
    final StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

}
