package pe.ayni.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.modulith.events.core.EventSerializer;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.events.StudentActivated;

/**
 * How the event publication registry writes an event down (TS04): as JSON, without the secrets and
 * the personal data three events carry.
 *
 * <p>An access link or a presence code is stored only as a hash and travels only by email, and
 * neither it nor an email address goes to an event that is persisted (backend guide, secrets and
 * personal data). The registry stores every event it delivers, so before it does, {@link
 * AccessRequested} loses its email and its link, {@link PresenceCodeIssued} its code, and {@link
 * StudentActivated} its email and its student code. Every other field, and every other event, is
 * stored as it is.
 *
 * <p>The listener that runs right after the commit still receives the event in full: it is the one
 * in memory. Only a delivery resumed from the registry reads the stored copy, and finds those
 * fields empty. A link or a code that was not delivered the first time is not delivered later
 * either, because none is kept to deliver; asking again issues a new one. The listeners of {@code
 * StudentActivated} never read the email nor the code, so a resumed activation still grants the
 * initial credits and still counts.
 *
 * <p>The same event always produces the same text, which the registry relies on to find the row of
 * a delivery it did not start itself.
 */
public class RedactingEventSerializer implements EventSerializer {

  private final ObjectMapper json;

  public RedactingEventSerializer(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public Object serialize(Object event) {
    try {
      return json.writeValueAsString(redacted(event));
    } catch (JsonProcessingException notJson) {
      throw new IllegalStateException(
          "Cannot store an event of type " + event.getClass().getName(), notJson);
    }
  }

  @Override
  public <T> T deserialize(Object serialized, Class<T> type) {
    try {
      return json.readerFor(type).readValue(serialized.toString());
    } catch (IOException unreadable) {
      throw new UncheckedIOException(
          "Cannot read a stored event of type " + type.getName(), unreadable);
    }
  }

  /** The event as it may be stored: the same one, unless it carries what must never be stored. */
  static Object redacted(Object event) {
    return switch (event) {
      case AccessRequested access ->
          new AccessRequested(
              access.tenantId(),
              null,
              access.purpose(),
              null,
              access.expiresAt(),
              access.occurredOn());
      case PresenceCodeIssued code ->
          new PresenceCodeIssued(
              code.tenantId(),
              code.sessionId(),
              code.userId(),
              null,
              code.expiresAt(),
              code.occurredOn());
      case StudentActivated student ->
          new StudentActivated(
              student.tenantId(), student.userId(), null, null, student.occurredOn());
      default -> event;
    };
  }
}
