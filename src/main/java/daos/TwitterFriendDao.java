package daos;

import static com.googlecode.objectify.ObjectifyService.ofy;
import java.util.Date;
import com.googlecode.objectify.Key;

import entities.TwitterFriend;

public class TwitterFriendDao {

  private TwitterFriend loadById(long id) {
    return ofy().load().key(Key.create(TwitterFriend.class, id)).now();
  }

  public void saveLastFollowedByDate(Long id, Date date) {
    TwitterFriend friend = loadById(id);
    if (friend == null) {
      friend = new TwitterFriend();
      friend.setId(id);
    }
    friend.setLastFollowedByDate(date);
    ofy().save().entities(friend).now();
  }

  public void saveLastFollowingDate(Long id, Date date) {
    TwitterFriend friend = loadById(id);
    if (friend == null) {
      friend = new TwitterFriend();
      friend.setId(id);
    }
    friend.setLastFollowingDate(date);
    ofy().save().entities(friend).now();
  }

  public void delete(long id) {
    ofy().delete().type(TwitterFriend.class).id(id).now();
  }

}
