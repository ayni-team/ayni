package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.shared.domain.Credits;

/**
 * Published by payments once the provider confirms a purchase, exactly once per purchase thanks to
 * its idempotency key. Wallet credits the purchased credits.
 */
public record PurchaseConfirmed(
    String tenantId, UUID purchaseId, UUID studentId, Credits credits, Instant occurredOn)
    implements DomainEvent {}
