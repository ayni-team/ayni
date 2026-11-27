package pe.ayni.paymentsfacade.application;

import java.time.Instant;
import java.util.UUID;

/**
 * The fact the payments service delivers from its outbox, as it travels:
 * {@code POST /internal/v1/events/purchase-confirmed}. {@code eventId} names this delivery's event;
 * the purchase is what the wallet credits once.
 */
public record PurchaseConfirmation(
    UUID eventId,
    String tenantId,
    UUID purchaseId,
    UUID studentId,
    Integer credits,
    Instant occurredOn) {}
