package entities;

import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

import lombok.Getter;
import lombok.Setter;

@Entity
public class Task {
  public static final int RUNNABLE = 0;
  public static final int RUNNING = 1;
  public static final int WAIT = 2;
  @Id
  @Getter
  @Setter
  private String id;
  @Getter
  @Setter
  private int status;
  @Getter
  @Setter
  private Date updateDate;
  @Getter
  @Setter
  private int seq;
}
