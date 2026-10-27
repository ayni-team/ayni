package pe.ayni.analytics.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * The record that a session, a student or a recognition was already counted.
 *
 * <p>Only identifiers: the source of the event and its kind. It is how the same event delivered
 * twice counts once, and no endpoint ever returns it.
 */
@Entity
@Immutable
@IdClass(CountedEventId.class)
@Table(schema = "analytics", name = "counted_events")
public class CountedEvent {

  @Id
  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Id
  @Column(name = "kind", length = 32, nullable = false, updatable = false)
  private String kind;

  @Id
  @Column(name = "source_id", nullable = false, updatable = false)
  private UUID sourceId;

  protected CountedEvent() {
    // Required by JPA
  }
}
