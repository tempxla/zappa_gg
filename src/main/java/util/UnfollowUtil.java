package util;

import java.util.regex.Pattern;

import twitter4j.User;

public class UnfollowUtil {

  private Pattern unfollowPattern;
  private static final int MAX_FOLLOW_COUNT = 5000;

  public UnfollowUtil() {
    final String pattern = String.join("|", "(fo[lr][lr]ow\\s*(me|back))", "(#拡散|#フォロバ)");
    unfollowPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
  }

  public boolean detectUnfollow(User user) {
    // 認証済みアカウント
    if (user.isVerified()) {
      return false;
    }
    // 言語
    String lang = user.getLang() == null ? "" : user.getLang();
    if (lang.equals("ar")) { // アラビア語
      if (user.getFriendsCount() >= MAX_FOLLOW_COUNT) {
        return true;
      }
    }
    // アイコン
    if (user.isDefaultProfileImage()) {
      return true;
    }
    // 説明文
    String description = user.getDescription() == null ? "" : user.getDescription();
    if (description.isEmpty()) {
      if (user.getFriendsCount() >= MAX_FOLLOW_COUNT) {
        return true;
      }
    }
    // 説明文
    boolean match = unfollowPattern.matcher(description).find();
    return match;
  }

}
