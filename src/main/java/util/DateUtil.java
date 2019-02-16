package util;

import java.util.Calendar;
import java.util.Date;

public class DateUtil {

  public static Date addDays(Date date, int days) {
    final Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    cal.add(Calendar.DATE, days);
    return cal.getTime();
  }
}
