package pe.ayni.notifications.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.CreditsExpiring;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.events.PurchaseConfirmed;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.events.ValidationResolved;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What notifications does about things that happened elsewhere: it tells the people concerned.
 *
 * <p>Nothing depends on this module, as the backend guide says. It reacts to events, and asks
 * identity only for the address of a person an event names. Each listener runs after the
 * publisher's transaction commits, so a notice for an event whose transaction rolled back is never
 * sent.
 *
 * <p>The university is bound from the event before anything is done; it may be none, for a
 * platform administrator.
 */
@Component
class NotificationEventListeners {

  private final DeliverAccessLinkUseCase deliverAccessLink;
  private final DeliverPresenceCodeUseCase deliverPresenceCode;
  private final DeliverTutorNoShowNotice deliverTutorNoShowNotice;
  private final DeliverPurchaseConfirmationUseCase deliverPurchaseConfirmation;
  private final DeliverBookingConfirmedNoticesUseCase deliverBookingConfirmed;
  private final DeliverBookingCancelledNoticeUseCase deliverBookingCancelled;
  private final DeliverCreditsExpiringNoticeUseCase deliverCreditsExpiring;
  private final DeliverSessionClosedNoticesUseCase deliverSessionClosed;
  private final DeliverRequestResolvedNoticeUseCase deliverRequestResolved;
  private final DeliverValidationResolvedNoticeUseCase deliverValidationResolved;

  NotificationEventListeners(
      DeliverAccessLinkUseCase deliverAccessLink,
      DeliverPresenceCodeUseCase deliverPresenceCode,
      DeliverTutorNoShowNotice deliverTutorNoShowNotice,
      DeliverPurchaseConfirmationUseCase deliverPurchaseConfirmation,
      DeliverBookingConfirmedNoticesUseCase deliverBookingConfirmed,
      DeliverBookingCancelledNoticeUseCase deliverBookingCancelled,
      DeliverCreditsExpiringNoticeUseCase deliverCreditsExpiring,
      DeliverSessionClosedNoticesUseCase deliverSessionClosed,
      DeliverRequestResolvedNoticeUseCase deliverRequestResolved,
      DeliverValidationResolvedNoticeUseCase deliverValidationResolved) {
    this.deliverAccessLink = deliverAccessLink;
    this.deliverPresenceCode = deliverPresenceCode;
    this.deliverTutorNoShowNotice = deliverTutorNoShowNotice;
    this.deliverPurchaseConfirmation = deliverPurchaseConfirmation;
    this.deliverBookingConfirmed = deliverBookingConfirmed;
    this.deliverBookingCancelled = deliverBookingCancelled;
    this.deliverCreditsExpiring = deliverCreditsExpiring;
    this.deliverSessionClosed = deliverSessionClosed;
    this.deliverRequestResolved = deliverRequestResolved;
    this.deliverValidationResolved = deliverValidationResolved;
  }

  @ApplicationModuleListener
  void on(AccessRequested event) {
    TenantContext.runAs(event.tenantId(), () -> deliverAccessLink.execute(event));
  }

  @ApplicationModuleListener
  void on(PresenceCodeIssued event) {
    TenantContext.runAs(event.tenantId(), () -> deliverPresenceCode.execute(event));
  }

  /**
   * A session was abandoned. The student is told only when they were there and the tutor was not:
   * that is the one case in which their credits come back. An absent student is told nothing.
   */
  @ApplicationModuleListener
  void on(SessionAbandoned event) {
    if (event.studentCheckedIn()) {
      TenantContext.runAs(event.tenantId(), () -> deliverTutorNoShowNotice.execute(event));
    }
  }

  @ApplicationModuleListener
  void on(PurchaseConfirmed event) {
    TenantContext.runAs(event.tenantId(), () -> deliverPurchaseConfirmation.execute(event));
  }

  @ApplicationModuleListener
  void on(BookingConfirmed event) {
    TenantContext.runAs(event.tenantId(), () -> deliverBookingConfirmed.execute(event));
  }

  @ApplicationModuleListener
  void on(BookingCancelled event) {
    TenantContext.runAs(event.tenantId(), () -> deliverBookingCancelled.execute(event));
  }

  @ApplicationModuleListener
  void on(CreditsExpiring event) {
    TenantContext.runAs(event.tenantId(), () -> deliverCreditsExpiring.execute(event));
  }

  @ApplicationModuleListener
  void on(SessionCompleted event) {
    TenantContext.runAs(event.tenantId(), () -> deliverSessionClosed.execute(event));
  }

  @ApplicationModuleListener
  void on(SessionUnverified event) {
    TenantContext.runAs(event.tenantId(), () -> deliverSessionClosed.execute(event));
  }

  @ApplicationModuleListener
  void on(RecognitionResolved event) {
    TenantContext.runAs(event.tenantId(), () -> deliverRequestResolved.execute(event));
  }

  @ApplicationModuleListener
  void on(ValidationResolved event) {
    TenantContext.runAs(event.tenantId(), () -> deliverValidationResolved.execute(event));
  }
}
