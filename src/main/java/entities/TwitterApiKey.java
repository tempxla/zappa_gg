package entities;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

import lombok.Getter;
import lombok.Setter;

@Entity
public class TwitterApiKey {

  @Id
  @Getter
  @Setter
  private String id;
  @Getter
  @Setter
  private String comsumerKey;
  @Getter
  @Setter
  private String comsumerSecret;
  @Getter
  @Setter
  private String accessToken;
  @Getter
  @Setter
  private String tokenSecret;

}
