package services;

import daos.TwitterApiKeyDao;
import entities.TwitterApiKey;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterService {

  public Twitter makeTwitterObject(String screenName) {
    TwitterApiKeyDao dao = new TwitterApiKeyDao();
    TwitterApiKey apiKey = dao.loadByKey(screenName);

    ConfigurationBuilder cb = new ConfigurationBuilder();
    cb.setDebugEnabled(true).setOAuthAccessToken(apiKey.getAccessToken())
        .setOAuthAccessTokenSecret(apiKey.getTokenSecret()).setOAuthConsumerKey(apiKey.getComsumerKey())
        .setOAuthConsumerSecret(apiKey.getComsumerSecret());

    return new TwitterFactory(cb.build()).getInstance();
  }

}
