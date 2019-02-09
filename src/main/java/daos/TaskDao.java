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
    Date date = new Date();
    initTask(TASK_TW_FOLLOWERS_LIST, date, 1);
    initTask(TASK_TW_FRIENDS_LIST, date, 2);
    initTask(TASK_GAE_FRIEND_LIST, date, 3);
  }

  private void initTask(String id, Date updateDate, int seq) {
    Task entity = new Task();
    entity.setId(id);
    entity.setStatus(Task.RUNNABLE);
    entity.setUpdateDate(updateDate);
    entity.setSeq(seq);
    ofy().save().entities(entity).now();
  }

  public void updateTask(String id, int status, Date date) {
    Task entity = loadById(id);
    entity.setStatus(status);
    entity.setUpdateDate(date);
    ofy().save().entities(entity).now();
  }

  public List<Task> loadAll() {
    return ofy().load().type(Task.class).list();
  }
}
