package daos;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Date;
import java.util.List;

import com.googlecode.objectify.Key;

import entities.Task;

public class TaskDao {

  public static final String TASK_GAE_FRIEND_LIST = "/gae/datastore/TwitterFriend";
  public static final String TASK_TW_FOLLOWERS_LIST = "/followers/list";
  public static final String TASK_TW_FRIENDS_LIST = "/friends/list";

  public Task loadById(String id) {
    return ofy().load().key(Key.create(Task.class, id)).now();
  }

  public void initAllTask() {
    initTask(TASK_GAE_FRIEND_LIST);
    initTask(TASK_TW_FOLLOWERS_LIST);
    initTask(TASK_TW_FRIENDS_LIST);
  }

  public void initTask(String id) {
    Task entity = new Task();
    entity.setId(id);
    entity.setStatus(Task.RUNNABLE);
    entity.setUpdateDate(new Date());
    switch (id) {
    case TASK_GAE_FRIEND_LIST:
      entity.setSeq(3);
      break;
    case TASK_TW_FOLLOWERS_LIST:
      entity.setSeq(1);
      break;
    case TASK_TW_FRIENDS_LIST:
      entity.setSeq(2);
      break;
    default:
      break;
    }
    ofy().save().entities(entity).now();
  }

  public void updateTask(String id, int status, Date date) {
    Task entity = loadById(id);
    entity.setId(id);
    entity.setStatus(status);
    entity.setUpdateDate(date);
    ofy().save().entities(entity).now();
  }

  public List<Task> loadAll() {
    return ofy().load().type(Task.class).list();
  }
}
