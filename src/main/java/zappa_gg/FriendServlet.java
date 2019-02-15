package zappa_gg;

import static util.WebConstant.MESSAGE_SUCCESS;
import static util.WebConstant.RES_ATR_MESSAGE;
import static util.WebConstant.URL_ADMIN;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import daos.TaskDao;
import entities.Task;
import services.FriendService;
import services.TwitterService;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import util.DateUtil;
import util.LogUtil;

/**
 * Servlet implementation class FriendServlet
 */
@SuppressWarnings("serial")
@WebServlet("/cron/FriendServlet")
public class FriendServlet extends HttpServlet {

  private static final Logger logger = Logger.getLogger(FriendServlet.class.getName());
  private static final int INIT_DAYS = 15;

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
   * @throws IOException
   * @throws ServletException
   */
  private void run(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Twitter tw = new TwitterService().makeTwitterObject(ZappaBot.SCREEN_NAME);
    TaskDao taskDao = new TaskDao();
    List<Task> tasks = taskDao.loadAll().stream().filter(Task::isEnabled).collect(Collectors.toList());
    Task oldTask = tasks.stream().min(Comparator.comparing(Task::getUpdateDate)).get();
    if (DateUtil.addDays(oldTask.getUpdateDate(), INIT_DAYS).compareTo(new Date()) < 0) {
      // 一定期間実行されないタスクがある場合は異常と見なし、初期状態に戻す。
      taskDao.resetTask(tasks);
      LogUtil.sendDirectMessage(tw, Messages.INIT_MESSAGE);
    } else if (tasks.stream().anyMatch(e -> e.getStatus() == Task.RUNNING)) {
      // 実行状態のタスクがある場合、優先して実行する。
      tasks.stream().filter(e -> e.getStatus() == Task.RUNNING).min(Comparator.comparing(Task::getSeq))
          .ifPresent(task -> runTask(tw, task));
    } else if (tasks.stream().anyMatch(e -> e.getStatus() == Task.RUNNABLE)) {
      // 実行可能状態のタスクがある場合、実行する。
      tasks.stream().filter(e -> e.getStatus() == Task.RUNNABLE).min(Comparator.comparing(Task::getSeq))
          .ifPresent(task -> runTask(tw, task));
    } else if (tasks.stream().allMatch(e -> e.getStatus() == Task.WAIT)) {
      // 全て待機状態の場合、初期状態に戻す。
      taskDao.resetTask(tasks);
    } else {
      logger.warning("<<<unreachable code>>>");
    }
    request.setAttribute(RES_ATR_MESSAGE, MESSAGE_SUCCESS);
    request.getRequestDispatcher(URL_ADMIN).forward(request, response);
  }

  private void runTask(Twitter tw, Task task) {
    logger.info(String.format("[START]%s", task.getId()));
    Date nowDate = new Date();
    boolean taskEnd = false;
    FriendService friendService = new FriendService();
    try {
      switch (task.getId()) {
      case TaskDao.TASK_GAE_FRIEND_LIST:
        taskEnd = friendService.updateFriendships(tw, nowDate);
        break;
      case TaskDao.TASK_TW_FOLLOWERS_LIST:
        taskEnd = friendService.updateFollowerDate(tw, nowDate);
        break;
      case TaskDao.TASK_TW_FRIENDS_LIST:
        taskEnd = friendService.updateFollowingDate(tw, nowDate);
        break;
      default:
        break;
      }
    } catch (TwitterException e) {
      logger.warning(e.toString());
    }
    int status = taskEnd ? Task.WAIT : Task.RUNNING;
    new TaskDao().updateTask(task.getId(), status, nowDate);
    logger.info(String.format("[END]%s (%d)", task.getId(), status));
  }

}
