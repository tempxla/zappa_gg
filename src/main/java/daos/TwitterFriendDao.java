package daos;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import entities.TwitterFriend;

public class TwitterFriendDao {

  private void saveLastDate(List<Long> ids, Consumer<TwitterFriend> setDate) {
    Set<Long> retainSet = new HashSet<>(ids);
    // LOAD
    Collection<TwitterFriend> entities = ofy().load().type(TwitterFriend.class).ids(ids).values();
    // UPATE
    for (TwitterFriend friend : entities) {
      setDate.accept(friend);
      retainSet.remove(friend.getId());
    }
    // INSERT
    for (Long id : retainSet) {
      TwitterFriend friend = new TwitterFriend();
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

  public void updateUnfollow(TwitterFriend entity, boolean unfollow) {
    entity.setUnfollow(unfollow);
    ofy().save().entities(entity).now();
  }

  public void unfollow(TwitterFriend entity) {
    entity.setLastFollowingDate(null);
    ofy().save().entities(entity).now();
  }

  public void delete(TwitterFriend user) {
    ofy().delete().entity(user).now();
  }
}
