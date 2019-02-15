package entities;

import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

import lombok.Getter;
import lombok.Setter;

@Entity
public class TwitterFriend {
  @Id
  @Getter
  @Setter
  private long id;
  @Getter
  @Setter
  private Date lastFollowingDate;
  @Getter
  @Setter
  private Date lastFollowedByDate;
  @Getter
  @Setter
  private boolean unfollow;
}
