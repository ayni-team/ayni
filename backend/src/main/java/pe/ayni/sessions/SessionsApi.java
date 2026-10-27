package pe.ayni.sessions;

import java.util.List;
import java.util.UUID;

/**
 * What sessions offers to the other modules.
 *
 * <p>Implemented by a class in {@code sessions.application}.
 */
public interface SessionsApi {

  /** @throws java.util.NoSuchElementException when the session does not exist in the current tenant */
  SessionView requireSession(UUID sessionId);

  /** Verified sessions the user taught, oldest first. Used by recognition. */
  List<SessionSummary> completedSessionsOf(UUID tutorId);
}
