package pe.ayni.audit.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One anomaly with what supports it: the pattern that raised it, the people it involves and the
 * sessions behind it.
 *
 * @param pattern the figures that made it an anomaly, as they were when it was detected
 * @param resolution {@code null} while the anomaly is open
 */
public record AnomalyDetail(
    UUID id,
    String kind,
    String severity,
    String status,
    String description,
    Instant detectedAt,
    Map<String, String> pattern,
    List<Person> people,
    List<Session> sessions,
    Resolution resolution) {

  /** Somebody the anomaly involves; the name is {@code null} when the university no longer has them. */
  public record Person(UUID id, String role, String fullName) {}

  /** A session that supports the anomaly; the times are {@code null} when sessions cannot say. */
  public record Session(UUID id, Instant scheduledStart, Instant scheduledEnd, String status) {}

  /** The decision a coordinator took. */
  public record Resolution(UUID resolvedBy, Instant resolvedAt, String reason) {}
}
