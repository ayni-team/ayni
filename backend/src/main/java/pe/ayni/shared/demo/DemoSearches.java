package pe.ayni.shared.demo;

import java.time.Instant;
import java.util.UUID;

/** What the history of the {@code demo} profile asks of matching. */
public interface DemoSearches {

  /** The student looks for tutors of a course in a window, as the search screen does. */
  void search(UUID studentId, UUID catalogItemId, Instant from, Instant to);
}
