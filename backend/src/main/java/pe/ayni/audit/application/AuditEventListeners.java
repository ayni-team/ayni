package pe.ayni.audit.application;

import static pe.ayni.audit.application.NewActivity.details;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.audit.domain.model.ActorRole;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.CreditsExpired;
import pe.ayni.shared.events.CreditsGranted;
import pe.ayni.shared.events.DomainEvent;
import pe.ayni.shared.events.RecognitionRequested;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionStarted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.events.ValidationResolved;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What audit writes down about things that happened elsewhere: one entry per event, saying who
 * acted and in what role, what happened, to which resource, and when.
 *
 * <p>Only the events that tell what happened to credits, bookings, sessions, skills and
 * recognition are heard. {@code AccessRequested} and {@code PresenceCodeIssued} are never heard
 * because they carry the access link and the presence code in clear, and nothing audit stores holds
 * an email address, a link or a code. Neither is {@code StudentActivated}, which carries the email.
 *
 * <p>Who acted is what the event says and nothing more, because asking another module is a
 * dependency the listeners of a log should not have: the person when the event names them, the role
 * with no person when it only implies one (a coordinator who decided), and {@code SYSTEM} for what
 * nobody asked for.
 *
 * <p>Recording is never allowed to be the reason an operation fails. Every listener catches
 * whatever goes wrong and says so in the log without the data of the event.
 */
@Component
class AuditEventListeners {

  private static final Logger log = LoggerFactory.getLogger(AuditEventListeners.class);

  private final RecordActivityUseCase recorder;

  AuditEventListeners(RecordActivityUseCase recorder) {
    this.recorder = recorder;
  }

  @ApplicationModuleListener
  void on(BookingConfirmed event) {
    record(
        event,
        new NewActivity(
            event.studentId(),
            ActorRole.STUDENT,
            "BOOKING_CONFIRMED",
            "BOOKING",
            event.bookingId(),
            details(
                "tutorId", event.tutorId(),
                "catalogItemId", event.catalogItemId(),
                "startsAt", event.startsAt(),
                "endsAt", event.endsAt(),
                "creditsCharged", event.creditsCharged() == null ? null : event.creditsCharged().amount()),
            event.occurredOn()));
  }

  @ApplicationModuleListener
  void on(BookingCancelled event) {
    BookingCancelled.CancelledBy by = event.cancelledBy();
    UUID actor = null;
    ActorRole role = ActorRole.SYSTEM;
    if (by == BookingCancelled.CancelledBy.STUDENT) {
      actor = event.studentId();
      role = ActorRole.STUDENT;
    } else if (by == BookingCancelled.CancelledBy.TUTOR) {
      actor = event.tutorId();
      role = ActorRole.TUTOR;
    }
    record(
        event,
        new NewActivity(
            actor,
            role,
            "BOOKING_CANCELLED",
            "BOOKING",
            event.bookingId(),
            details(
                "studentId", event.studentId(),
                "tutorId", event.tutorId(),
                "late", event.late()),
            event.occurredOn()));
  }

  @ApplicationModuleListener
  void on(SessionStarted event) {
    record(
        event,
        new NewActivity(
            null,
            ActorRole.SYSTEM,
            "SESSION_STARTED",
            "SESSION",
            event.sessionId(),
            details("bookingId", event.bookingId()),
            event.occurredOn()));
  }

  @ApplicationModuleListener
  void on(SessionCompleted event) {
    record(
        event,
        new NewActivity(
            null,
            ActorRole.SYSTEM,
            "SESSION_COMPLETED",
            "SESSION",
            event.sessionId(),
            details(
                "bookingId", event.bookingId(),
                "tutorId", event.tutorId(),
                "studentId", event.studentId(),
                "catalogItemId", event.catalogItemId(),
                "creditsEarned", event.creditsEarned() == null ? null : event.creditsEarned().amount()),
            event.occurredOn()));
  }

  @ApplicationModuleListener
  void on(SessionUnverified event) {
    record(
        event,
        new NewActivity(
            null,
            ActorRole.SYSTEM,
            "SESSION_UNVERIFIED",
            "SESSION",
            event.sessionId(),
            details(
                "bookingId", event.bookingId(),
                "tutorId", event.tutorId(),
                "studentId", event.studentId(),
                "unconfirmedUserIds", event.unconfirmedUserIds()),
            event.occurredOn()));
  }

  @ApplicationModuleListener
  void on(SessionAbandoned event) {
    record(
        event,
        new NewActivity(
            null,
            ActorRole.SYSTEM,
            "SESSION_ABANDONED",
            "SESSION",
            event.sessionId(),
            details(
                "bookingId", event.bookingId(),
                "tutorId", event.tutorId(),
                "studentId", event.studentId(),
                "studentCheckedIn", event.studentCheckedIn()),
            event.occurredOn()));
  }

  @ApplicationModuleListener
  void on(CreditsGranted event) {
    record(
        event,
        new NewActivity(
            null,
            ActorRole.SYSTEM,
            "CREDITS_GRANTED",
            "WALLET",
            event.userId(),
            details(
                "amount", event.amount() == null ? null : event.amount().amount(),
                "type", event.type(),
                "expiresAt", event.expiresAt()),
            event.occurredOn()));
  }

  @ApplicationModuleListener
  void on(CreditsExpired event) {
    record(
        event,
        new NewActivity(
            null,
            ActorRole.SYSTEM,
            "CREDITS_EXPIRED",
            "WALLET",
            event.userId(),
            details("amount", event.amount() == null ? null : event.amount().amount()),
            event.occurredOn()));
  }

  @ApplicationModuleListener
  void on(SkillEnabled event) {
    record(
        event,
        new NewActivity(
            null,
            ActorRole.SYSTEM,
            "SKILL_ENABLED",
            "CATALOG_ITEM",
            event.catalogItemId(),
            details("tutorId", event.tutorId()),
            event.occurredOn()));
  }

  @ApplicationModuleListener
  void on(SkillWithdrawn event) {
    record(
        event,
        new NewActivity(
            null,
            ActorRole.SYSTEM,
            "SKILL_WITHDRAWN",
            "CATALOG_ITEM",
            event.catalogItemId(),
            details("tutorId", event.tutorId()),
            event.occurredOn()));
  }

  /**
   * A coordinator approved or rejected the evidence for a global tool. The event does not say which
   * coordinator and carries a free text reason, which is left out: it stays where it was written.
   */
  @ApplicationModuleListener
  void on(ValidationResolved event) {
    record(
        event,
        new NewActivity(
            null,
            ActorRole.COORDINATOR,
            event.approved() ? "VALIDATION_APPROVED" : "VALIDATION_REJECTED",
            "VALIDATION_REQUEST",
            event.validationRequestId(),
            details("tutorId", event.tutorId(), "catalogItemId", event.catalogItemId()),
            event.occurredOn()));
  }

  @ApplicationModuleListener
  void on(RecognitionRequested event) {
    record(
        event,
        new NewActivity(
            event.studentId(),
            ActorRole.STUDENT,
            "RECOGNITION_REQUESTED",
            "RECOGNITION_REQUEST",
            event.requestId(),
            details("totalHours", event.totalHours()),
            event.occurredOn()));
  }

  /**
   * A coordinator decided a recognition request. The event does not say which coordinator and
   * carries a free text reason, which is left out: it stays with the decision, in recognition.
   */
  @ApplicationModuleListener
  void on(RecognitionResolved event) {
    record(
        event,
        new NewActivity(
            null,
            ActorRole.COORDINATOR,
            event.approved() ? "RECOGNITION_APPROVED" : "RECOGNITION_REJECTED",
            "RECOGNITION_REQUEST",
            event.requestId(),
            details("studentId", event.studentId()),
            event.occurredOn()));
  }

  private void record(DomainEvent event, NewActivity activity) {
    try {
      TenantContext.runAs(event.tenantId(), () -> recorder.record(activity));
    } catch (RuntimeException failure) {
      // What was published has already happened: an entry that could not be written must not
      // undo it. The data of the event stays out of the log, whatever the failure says.
      log.warn(
          "Could not record {} of university {}: {}",
          activity.action(),
          event.tenantId(),
          failure.getClass().getSimpleName());
    }
  }
}
