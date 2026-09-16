package pe.ayni.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published by booking when new one hour blocks become available for a tutor.
 *
 * <p>Matching adds one offer per block and per skill the tutor has enabled.
 */
public record HoursGenerated(String tenantId, UUID tutorId, List<Block> blocks, Instant occurredOn)
    implements DomainEvent {

  /** One generated hour. */
  public record Block(UUID blockId, Instant startsAt) {}
}
