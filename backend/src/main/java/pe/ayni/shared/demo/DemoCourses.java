package pe.ayni.shared.demo;

import java.util.Optional;
import java.util.UUID;

/** What the history of the {@code demo} profile asks of skills. */
public interface DemoCourses {

  /** The catalogue item of a course of the university bound to the thread, by its code. */
  Optional<UUID> courseCoded(String courseCode);
}
