package daos;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.googlecode.objectify.cmd.Query;

import entities.TwitterFriend;

public class TwitterFriendDao {

  private void saveLastDate(List<Long> ids, Consumer<TwitterFriend> setDate) {
    final Set<Long> retainSet = new HashSet<>(ids);
    // LOAD
    final Collection<TwitterFriend> loaded = ofy().load().type(TwitterFriend.class).ids(ids).values();
    final List<TwitterFriend> entities = new ArrayList<>(loaded);
    // UPATE
    for (TwitterFriend friend : entities) {
      setDate.accept(friend);
      retainSet.remove(friend.getId());
    }
    // INSERT
    for (Long id : retainSet) {
      final TwitterFriend friend = new TwitterFriend();
      friend.setId(id);
      setDate.accept(friend);
      entities.add(friend);
    }
    ofy().save().entities(entities).now();
  }

  public void saveLastFollowedByDate(List<Long> ids, Date date) {
    saveLastDate(ids, friend -> friend.setLastFollowedByDate(date));
  }

  public void saveLastFollowingDate(List<Long> ids, Date date) {
    saveLastDate(ids, friend -> friend.setLastFollowingDate(date));
  }

  public void save(List<TwitterFriend> entities) {
    ofy().save().entities(entities).now();
  }

  public void delete(List<TwitterFriend> entities) {
    ofy().delete().entities(entities).now();
  }

  public Query<TwitterFriend> makeQuery(int limit) {
    return ofy().load().type(TwitterFriend.class).limit(limit);
  }

}
