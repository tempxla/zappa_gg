package daos;

import static com.googlecode.objectify.ObjectifyService.ofy;

import com.googlecode.objectify.Key;

import entities.TwitterApiKey;

public class TwitterApiKeyDao {
  public TwitterApiKey loadByKey(String key) {
    return ofy().load().key(Key.create(TwitterApiKey.class, key)).now();
  }

  public void saveTwitterApiKey(TwitterApiKey entity) {
    ofy().save().entities(entity).now();
  }

}
