package pe.ayni.wallet.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.PurchaseConfirmed;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What wallet does about things that happened elsewhere.
 *
 * <p>These are facts, not orders: sessions says a session ended and does not know that credits
 * exist. Each listener is the whole of wallet's reaction to one fact, which is why adding a
 * consequence later means adding a method here rather than editing the module that published it.
 *
 * <p>Every one of them binds the university from the event before doing anything. A listener runs
 * after the original request is over, possibly on another thread, so the university it belongs to
 * can only come from the event itself. That is why every event carries it.
 */
@Component
class WalletEventListeners {

  private static final Logger log = LoggerFactory.getLogger(WalletEventListeners.class);

  private final GrantCredits grant;
  private final RefundBooking refund;

  WalletEventListeners(GrantCredits grant, RefundBooking refund) {
    this.grant = grant;
    this.refund = refund;
  }

  /**
   * A session ended with both participants verified: the tutor earns one credit per hour taught.
   *
   * <p>Earned credits never expire and are the only ones that count towards recognition, which is
   * the reason the presence check exists at all. A session that ends unverified publishes {@code
   * SessionUnverified} instead and nothing arrives here.
   */
  @ApplicationModuleListener
  void on(SessionCompleted event) {
    if (event.creditsEarned().isZero()) {
      // Nothing was taught. Not an error, and not a movement either.
      return;
    }
    TenantContext.runAs(
        event.tenantId(),
        () ->
            grant.grant(
                event.tutorId(),
                event.creditsEarned(),
                CreditType.EARNED,
                null,
                event.sessionId()));
    log.debug(
        "Credited {} earned credits to tutor {}", event.creditsEarned().amount(), event.tutorId());
  }

  /**
   * A booking was cancelled: cancelling always refunds.
   *
   * <p>Whether it was late, and who it counts against, is booking's business. Wallet returns the
   * credits either way.
   */
  @ApplicationModuleListener
  void on(BookingCancelled event) {
    TenantContext.runAs(event.tenantId(), () -> refund.refund(event.bookingId()));
    log.debug("Refunded booking {}", event.bookingId());
  }

  /** A purchase was confirmed: the credits are placed, and they never expire. */
  @ApplicationModuleListener
  void on(PurchaseConfirmed event) {
    TenantContext.runAs(
        event.tenantId(),
        () ->
            grant.grant(
                event.studentId(),
                event.credits(),
                CreditType.PURCHASED,
                null,
                event.purchaseId()));
    log.debug("Credited {} purchased credits to {}", event.credits().amount(), event.studentId());
  }
}
