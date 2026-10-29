package pe.ayni.shared.demo;

import java.util.Map;
import java.util.UUID;

/**
 * What the history of the {@code demo} profile asks of sessions, in the university bound to the
 * thread and at the time the clock reads.
 */
public interface DemoSessions {

  /** The session a confirmed booking created. */
  UUID sessionOf(UUID bookingId);

  /** The participant enters the room. */
  void join(UUID sessionId, UUID userId);

  /**
   * Issues the presence codes of a session in progress and confirms both, with the codes as they
   * left in {@code PresenceCodeIssued}: the email they travel in is not read.
   */
  void confirmPresence(UUID sessionId);

  /** The participant puts one element on the whiteboard of a session in progress. */
  void draw(UUID sessionId, UUID userId, String elementId, String type, Map<String, Object> data);

  /** The participant confirms the end. */
  void end(UUID sessionId, UUID userId);

  /** Abandons the session if somebody did not enter before the ten minute mark. */
  void abandonIfAbsent(UUID sessionId);
}
