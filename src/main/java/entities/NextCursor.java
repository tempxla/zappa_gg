package entities;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

import lombok.Getter;
import lombok.Setter;


@Entity
public class NextCursor {
  @Id
  @Getter
  @Setter
  private String id;
  @Getter
  @Setter
  private String nextCursor;
}
