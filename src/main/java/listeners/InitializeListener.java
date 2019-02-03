package listeners;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.googlecode.objectify.ObjectifyService;

import entities.NextCursor;
import entities.Task;
import entities.TwitterApiKey;
import entities.TwitterFriend;

/**
 * Application Lifecycle Listener implementation class InitListener
 *
 */
@WebListener
public class InitializeListener implements ServletContextListener {

  /**
   * Default constructor.
   */
  public InitializeListener() {
  }

  /**
   * @see ServletContextListener#contextDestroyed(ServletContextEvent)
   */
  @Override
  public void contextDestroyed(ServletContextEvent sce) {
  }

  /**
   * @see ServletContextListener#contextInitialized(ServletContextEvent)
   */
  @Override
  public void contextInitialized(ServletContextEvent sce) {
    ObjectifyService.register(TwitterApiKey.class);
    ObjectifyService.register(NextCursor.class);
    ObjectifyService.register(TwitterFriend.class);
    ObjectifyService.register(Task.class);
  }

}
