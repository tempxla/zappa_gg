package daos;

import static com.googlecode.objectify.ObjectifyService.ofy;

import com.googlecode.objectify.Key;

import entities.NextCursor;

public class NextCursorDao {

  public NextCursor loadById(String id) {
    return ofy().load().key(Key.create(NextCursor.class, id)).now();
  }

  public void insertNextCursor(String id, String cursor) {
    final NextCursor entity = new NextCursor();
    entity.setId(id);
    entity.setNextCursor(cursor);
    ofy().save().entities(entity).now();
  }

  public void updateNextCursor(NextCursor entity, String cursor) {
    entity.setNextCursor(cursor);
    ofy().save().entities(entity).now();
  }
}
