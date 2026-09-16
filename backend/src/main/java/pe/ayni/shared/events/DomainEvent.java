package pe.ayni.shared.events;

import java.time.Instant;

/**
 * Contract for every event published by one module and consumed by another.
 *
 * <p>Events live here, and not inside the module that publishes them, so that neither side depends
 * on the other: both depend on this package. If {@code SessionCompleted} lived in {@code sessions},
 * then {@code wallet} would have to depend on {@code sessions} just to listen to it.
 *
 * <p>Every event states which university it belongs to, because whoever reacts to it runs outside
 * the original request and has no other way of knowing.
 */
public interface DomainEvent {

  /** University that owns the aggregate the event originated from. */
  String tenantId();

  /** Moment the event happened, in UTC. */
  Instant occurredOn();
}
