package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by reputation when a participant rates a session.
 *
 * <p>Matching reacts to a student's rating by refreshing the tutor's standing in its projection,
 * asking reputation for the new figures.
 *
 * @param stars {@code null} when the tutor rated the student
 */
public record SessionRated(
    String tenantId,
    UUID sessionId,
    UUID ratedUserId,
    UUID catalogItemId,
    Direction direction,
    Integer stars,
    Instant occurredOn)
    implements DomainEvent {

  /** Who rated whom. */
  public enum Direction {
    STUDENT_TO_TUTOR,
    TUTOR_TO_STUDENT
  }
}
