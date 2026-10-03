package pe.ayni.wallet.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.PurchaseConfirmed;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What wallet does about things that happened elsewhere.
 *
 * <p>These are facts, not orders: the module that publishes an event does not know what Wallet
 * will do with it. Each listener is Wallet's reaction to one fact.
 *
 * <p>Every listener binds the university from the event before doing anything. A listener can run
 * after the original request is over, possibly on another thread, so TenantContext cannot come from
 * that request.
 */
@Component
class WalletEventListeners {

  private static final Logger log =
          LoggerFactory.getLogger(WalletEventListeners.class);

  private final GrantInitialCredits initialCredits;
  private final GrantCredits grant;
  private final RefundBooking refund;

  WalletEventListeners(
          GrantInitialCredits initialCredits,
          GrantCredits grant,
          RefundBooking refund) {

    this.initialCredits = initialCredits;
    this.grant = grant;
    this.refund = refund;
  }

  /**
   * A student entered Ayni for the first time with their academic identity already established.
   *
   * <p>The university's current baseline policy determines the amount and lifetime of the initial
   * credits. GrantInitialCredits makes this reaction idempotent.
   */
  @ApplicationModuleListener
  void on(StudentActivated event) {

    TenantContext.runAs(
            event.tenantId(),
            () -> initialCredits.grantFor(event));

    log.debug(
            "Handled initial credits for activated student {}",
            event.userId());
  }

  /**
   * A session ended with both participants verified: the tutor earns one credit per hour taught.
   *
   * <p>Earned credits never expire and are the only ones that count towards recognition.
   */
  @ApplicationModuleListener
  void on(SessionCompleted event) {

    if (event.creditsEarned().isZero()) {
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
            "Credited {} earned credits to tutor {}",
            event.creditsEarned().amount(),
            event.tutorId());
  }

  /**
   * A booking was cancelled: cancelling always refunds.
   */
  @ApplicationModuleListener
  void on(BookingCancelled event) {

    TenantContext.runAs(
            event.tenantId(),
            () -> refund.refund(event.bookingId()));

    log.debug(
            "Refunded booking {}",
            event.bookingId());
  }

  /**
   * A session ended without every participant proving their presence.
   */
  @ApplicationModuleListener
  void on(SessionUnverified event) {

    TenantContext.runAs(
            event.tenantId(),
            () -> refund.refund(event.bookingId()));

    log.debug(
            "Refunded booking {} of unverified session {}",
            event.bookingId(),
            event.sessionId());
  }

  /**
   * A purchase was confirmed: the credits are placed and never expire.
   */
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

    log.debug(
            "Credited {} purchased credits to {}",
            event.credits().amount(),
            event.studentId());
  }
}