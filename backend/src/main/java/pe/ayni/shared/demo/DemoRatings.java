package pe.ayni.shared.demo;

import java.util.Set;
import java.util.UUID;

/** What the history of the {@code demo} profile asks of reputation. */
public interface DemoRatings {

  /** The student rates the tutor of a completed session. */
  void rateTutor(UUID sessionId, UUID studentId, int stars, Set<String> tags, String comment);

  /** The tutor answers the three facts about the student of a completed session. */
  void rateStudent(UUID sessionId, UUID tutorId, boolean punctual, boolean connectionOk,
      boolean flowed, String comment);
}
