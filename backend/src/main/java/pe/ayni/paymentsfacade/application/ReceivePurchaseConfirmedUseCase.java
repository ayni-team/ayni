package pe.ayni.paymentsfacade.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.PurchaseConfirmed;

/**
 * A purchase the payments service confirmed reaches the monolith (ADR 0011).
 *
 * <p>It becomes the same {@link PurchaseConfirmed} the payments module used to publish, so wallet
 * credits it and notifications emails the student exactly as before, and neither knows the
 * purchase came over the network. It is published inside a transaction, which is what makes the
 * event publication registry store it before this returns (TS04): the 202 the service receives
 * means the event will be delivered to both, after a restart if need be.
 *
 * <p>The service delivers at least once, so the same purchase may arrive twice. That is safe by
 * construction (Idempotent Consumer): wallet grants with {@code grantOnce}, keyed by the purchase,
 * and notifications records one notice per purchase. Nothing here has to remember what it received.
 */
@Service
public class ReceivePurchaseConfirmedUseCase {

  private static final Logger log = LoggerFactory.getLogger(ReceivePurchaseConfirmedUseCase.class);

  private final ApplicationEventPublisher events;

  ReceivePurchaseConfirmedUseCase(ApplicationEventPublisher events) {
    this.events = events;
  }

  @Transactional
  public void execute(PurchaseConfirmation confirmation) {
    check(confirmation);
    events.publishEvent(
        new PurchaseConfirmed(
            confirmation.tenantId().strip(),
            confirmation.purchaseId(),
            confirmation.studentId(),
            Credits.of(confirmation.credits()),
            confirmation.occurredOn()));
    log.debug(
        "Received PurchaseConfirmed {} for purchase {}",
        confirmation.eventId(),
        confirmation.purchaseId());
  }

  private static void check(PurchaseConfirmation confirmation) {
    if (confirmation == null
        || confirmation.tenantId() == null
        || confirmation.tenantId().isBlank()
        || confirmation.purchaseId() == null
        || confirmation.studentId() == null
        || confirmation.occurredOn() == null) {
      throw new InvalidPurchaseConfirmation(
          "tenantId, purchaseId, studentId, credits and occurredOn are required");
    }
    if (confirmation.credits() == null || confirmation.credits() < 1) {
      throw new InvalidPurchaseConfirmation("credits must be at least 1");
    }
  }
}
