package pe.ayni.payments.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.payments.application.PurchaseEvents;
import pe.ayni.payments.domain.model.Purchase;

/**
 * {@link PurchaseEvents} as a Transactional Outbox (ADR 0011): the fact is a row of {@code
 * payments.outbox}, inserted in the transaction that confirms the purchase. A rollback takes both
 * away; a commit keeps both, and {@link OutboxRelay} delivers the row afterwards, however long the
 * monolith takes to be reachable.
 *
 * <p>The payload is the contract of {@code POST /internal/v1/events/purchase-confirmed}: the same
 * fields as the monolith's {@code PurchaseConfirmed}, with the event's own id so the monolith can
 * tell a delivery repeated after a lost answer.
 */
@Component
class OutboxPurchaseEvents implements PurchaseEvents {

  static final String PURCHASE_CONFIRMED = "PurchaseConfirmed";

  private final OutboxRepository outbox;
  private final ObjectMapper json;
  private final Clock clock;

  OutboxPurchaseEvents(OutboxRepository outbox, ObjectMapper json, Clock clock) {
    this.outbox = outbox;
    this.json = json;
    this.clock = clock;
  }

  /** Only inside the transaction that confirms: there is no outbox without one. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void purchaseConfirmed(Purchase purchase) {
    UUID eventId = UUID.randomUUID();
    Instant now = clock.instant();
    outbox.record(
        eventId,
        purchase.getTenantId(),
        PURCHASE_CONFIRMED,
        purchase.getId(),
        payload(
            new PurchaseConfirmedPayload(
                eventId,
                purchase.getTenantId(),
                purchase.getId(),
                purchase.getStudentId(),
                purchase.getCredits(),
                purchase.getConfirmedAt())),
        now);
  }

  private String payload(PurchaseConfirmedPayload event) {
    try {
      return json.writeValueAsString(event);
    } catch (JsonProcessingException impossible) {
      throw new IllegalStateException("A PurchaseConfirmed payload could not be written", impossible);
    }
  }

  /** What the monolith receives. */
  record PurchaseConfirmedPayload(
      UUID eventId,
      String tenantId,
      UUID purchaseId,
      UUID studentId,
      int credits,
      Instant occurredOn) {}
}
