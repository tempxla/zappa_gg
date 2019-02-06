package admin;

import static util.WebConstant.MESSAGE_ERROR;
import static util.WebConstant.MESSAGE_NOT_FOUND;
import static util.WebConstant.MESSAGE_NO_PARAMS;
import static util.WebConstant.MESSAGE_SUCCESS;
import static util.WebConstant.RES_ATR_MESSAGE;
import static util.WebConstant.URL_ADMIN;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import daos.TaskDao;
import daos.TwitterApiKeyDao;
import entities.TwitterApiKey;
import services.FriendService;
import services.TwitterService;
import twitter4j.PagableResponseList;
import twitter4j.RateLimitStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import zappa_gg.ZappaBot;

/**
 * Servlet implementation class AdminServlet
 */
@SuppressWarnings("serial")
@WebServlet("/admin/AdminServlet")
public class AdminServlet extends HttpServlet {

  // リクエストパラメータ
  private static final String REQ_PRM_FUNC = "func";

  // リクエスト・レスポンス共通パラメータ
  private static final String REQ_RES_COMSUMER_KEY = "comsumerKey";
  private static final String REQ_RES_COMSUMER_SECRET = "comsumerSecret";
  private static final String REQ_RES_ACCESS_TOKEN = "accessToken";
  private static final String REQ_RES_TOKEN_SECRET = "tokenSecret";

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
   * @throws ServletException
   * @throws IOException
   */
  private void run(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String func = request.getParameter(REQ_PRM_FUNC);
    if (func == null || func.isEmpty()) {
      setErrorMessage(request, MESSAGE_NO_PARAMS);
      forwardAdminPage(request, response);
      return;
    }
    try {
      Method method = this.getClass().getMethod(func, HttpServletRequest.class, HttpServletResponse.class);
      method.invoke(this, request, response);
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException e) {
      setErrorMessage(request, e.toString());
      forwardAdminPage(request, response);
    }
  }

  // ----------------------------------------------------------------------------------------------
  // API KEY
  // ----------------------------------------------------------------------------------------------
  /**
   * ツイッターAPIキーの情報をDBに保存する。
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  public void update(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    // 保存
    TwitterApiKey entity = new TwitterApiKey();
    entity.setId(ZappaBot.SCREEN_NAME);
    entity.setComsumerKey(request.getParameter(REQ_RES_COMSUMER_KEY));
    entity.setComsumerSecret(request.getParameter(REQ_RES_COMSUMER_SECRET));
    entity.setAccessToken(request.getParameter(REQ_RES_ACCESS_TOKEN));
    entity.setTokenSecret(request.getParameter(REQ_RES_TOKEN_SECRET));
    new TwitterApiKeyDao().saveTwitterApiKey(entity);
    // 保存成功
    setSuccessMessage(request);
    request.setAttribute(REQ_RES_COMSUMER_KEY, entity.getComsumerKey());
    request.setAttribute(REQ_RES_COMSUMER_SECRET, entity.getComsumerSecret());
    request.setAttribute(REQ_RES_ACCESS_TOKEN, entity.getAccessToken());
    request.setAttribute(REQ_RES_TOKEN_SECRET, entity.getTokenSecret());
    forwardAdminPage(request, response);
  }

  /**
   * TWITTER API KEY をDBから取得する。
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  public void load(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    TwitterApiKey entity = new TwitterApiKeyDao().loadByKey(ZappaBot.SCREEN_NAME);
    if (entity == null) {
      setSuccessMessage(request, MESSAGE_NOT_FOUND);
    } else {
      request.setAttribute(REQ_RES_COMSUMER_KEY, entity.getComsumerKey());
      request.setAttribute(REQ_RES_COMSUMER_SECRET, entity.getComsumerSecret());
      request.setAttribute(REQ_RES_ACCESS_TOKEN, entity.getAccessToken());
      request.setAttribute(REQ_RES_TOKEN_SECRET, entity.getTokenSecret());
      setSuccessMessage(request);
    }
    forwardAdminPage(request, response);
  }

  // ----------------------------------------------------------------------------------------------
  // FRIENDS MANAGEMENT
  // ----------------------------------------------------------------------------------------------
  /**
   * フォロワーのリストをTwitterから取得し、DBに保存する。
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  public void loadFollower(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    Twitter tw = new TwitterService().makeTwitterObject(ZappaBot.SCREEN_NAME);
    try {
      new FriendService().updateFollowerDate(tw);
      setSuccessMessage(request);
    } catch (TwitterException e) {
      setErrorMessage(request, e.toString());
    }
    forwardAdminPage(request, response);
  }

  /**
   * フォローのリストをTwitterから取得し、DBに保存する。
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  public void loadFollowing(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    Twitter tw = new TwitterService().makeTwitterObject(ZappaBot.SCREEN_NAME);
    try {
      new FriendService().updateFollowingDate(tw);
      setSuccessMessage(request);
    } catch (TwitterException e) {
      setErrorMessage(request, e.toString());
    }
    forwardAdminPage(request, response);
  }

  /**
   * Twitter API 制限ステータスを取得する。
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  public void showStatus(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    Twitter tw = new TwitterService().makeTwitterObject(ZappaBot.SCREEN_NAME);
    try {
      Map<String, RateLimitStatus> status = new FriendService().loadRateLimitStatus(tw);
      StringBuilder sb = new StringBuilder();
      status.entrySet().stream().forEach(e -> {
        sb.append(e.getKey());
        sb.append(System.lineSeparator());
        sb.append(e.getValue().toString());
        sb.append(System.lineSeparator());
      });
      setSuccessMessage(request, sb.toString());
    } catch (TwitterException e) {
      setErrorMessage(request, e.toString());
    }
    forwardAdminPage(request, response);
  }

  /**
   * DBの情報を元に、フォロー/リムーブを実行する。
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  public void updateFriendship(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    Twitter tw = new TwitterService().makeTwitterObject(ZappaBot.SCREEN_NAME);
    try {
      new FriendService().updateFriendships(tw);
      setSuccessMessage(request);
    } catch (TwitterException e) {
      setErrorMessage(request, e.toString());
    }
    forwardAdminPage(request, response);
  }

  // ----------------------------------------------------------------------------------------------
  // TASK MANAGEMENT
  // ----------------------------------------------------------------------------------------------
  /**
   * タスクの状態を初期化する。
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  public void initTask(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    TaskDao dao = new TaskDao();
    dao.initAllTask();
    setSuccessMessage(request);
    forwardAdminPage(request, response);
  }

  // ----------------------------------------------------------------------------------------------
  // COMMON
  // ----------------------------------------------------------------------------------------------
  /**
   * 画面をクリアする。
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  public void clear(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    forwardAdminPage(request, response);
  }

  /**
   * Debug用
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  public void debug(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Twitter tw = new TwitterService().makeTwitterObject(ZappaBot.SCREEN_NAME);
    try {
      Set<Long> followList = tw.getUserListMembers(ZappaBot.SCREEN_NAME, "follow", 100, PagableResponseList.START, true)
          .stream().map(User::getId).collect(Collectors.toSet());
      setSuccessMessage(request, followList.toString());
    } catch (TwitterException e) {
      setErrorMessage(request, e.toString());
    }
    forwardAdminPage(request, response);
  }

  public void debug2(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Twitter tw = new TwitterService().makeTwitterObject(ZappaBot.SCREEN_NAME);
    try {
      Set<String> replied = new HashSet<>();
      Set<String> speakList = tw
          .getUserListMembers(ZappaBot.SCREEN_NAME, "speak", 100, PagableResponseList.START, true)
          .stream().map(User::getScreenName).filter(s -> !replied .contains(s)).collect(Collectors.toSet());
      setSuccessMessage(request, speakList.toString());
    } catch (TwitterException e) {
      setErrorMessage(request, e.toString());
    }
    forwardAdminPage(request, response);
  }

  /**
   * 成功メッセージ
   *
   * @param request
   */
  private void setSuccessMessage(HttpServletRequest request) {
    request.setAttribute(RES_ATR_MESSAGE, MESSAGE_SUCCESS);
  }

  /**
   * 成功メッセージ
   *
   * @param request
   * @param message
   */
  private void setSuccessMessage(HttpServletRequest request, String message) {
    request.setAttribute(RES_ATR_MESSAGE, MESSAGE_SUCCESS + System.lineSeparator() + message);
  }

  /**
   * 失敗メッセージ
   *
   * @param request
   * @param message
   */
  private void setErrorMessage(HttpServletRequest request, String message) {
    request.setAttribute(RES_ATR_MESSAGE, MESSAGE_ERROR + System.lineSeparator() + message);
  }

  /**
   * 管理者ページへ
   *
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  private void forwardAdminPage(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    RequestDispatcher rd = request.getRequestDispatcher(URL_ADMIN);
    rd.forward(request, response);
  }

}
