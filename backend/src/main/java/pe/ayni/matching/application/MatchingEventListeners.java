package pe.ayni.matching.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.HoursGenerated;
import pe.ayni.shared.events.HoursWithdrawn;
import pe.ayni.shared.events.SessionRated;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What matching does about things that happened elsewhere: it keeps its search projection in step.
 *
 * <p>Nobody tells matching to add or remove an offer. Booking announces hours and bookings, skills
 * announces courses, reputation announces ratings, and each of them works without knowing the
 * search exists. Each listener runs after the publisher's transaction commits, so an hour whose
 * generation rolled back is never offered.
 *
 * <p>Every listener binds the university from the event before doing anything, because it runs
 * outside the request that caused it.
 */
@Component
class MatchingEventListeners {

  private static final Logger log = LoggerFactory.getLogger(MatchingEventListeners.class);

  private final OfferProjection projection;

  MatchingEventListeners(OfferProjection projection) {
    this.projection = projection;
  }

  @ApplicationModuleListener
  void on(HoursGenerated event) {
    TenantContext.runAs(
        event.tenantId(),
        () -> {
          int offered =
              projection.offerHours(
                  event.tutorId(),
                  event.blocks().stream()
                      .map(block -> new OpenHour(block.blockId(), block.startsAt()))
                      .toList());
          log.debug("Offered {} new hours of tutor {}", offered, event.tutorId());
        });
  }

  /** A course enabled after the tutor's hours exist is offered in the hours they still have open. */
  @ApplicationModuleListener
  void on(SkillEnabled event) {
    TenantContext.runAs(
        event.tenantId(),
        () -> {
          int offered = projection.offerCourse(event.tutorId(), event.catalogItemId());
          log.debug(
              "Offered course {} of tutor {} in {} hours",
              event.catalogItemId(),
              event.tutorId(),
              offered);
        });
  }

  @ApplicationModuleListener
  void on(SkillWithdrawn event) {
    TenantContext.runAs(
        event.tenantId(),
        () -> projection.withdrawCourse(event.tutorId(), event.catalogItemId()));
  }

  @ApplicationModuleListener
  void on(HoursWithdrawn event) {
    TenantContext.runAs(event.tenantId(), () -> projection.withdrawHours(event.blockIds()));
  }

  /** A booked hour is nobody else's to find. */
  @ApplicationModuleListener
  void on(BookingConfirmed event) {
    TenantContext.runAs(event.tenantId(), () -> projection.withdrawHours(event.blockIds()));
  }

  @ApplicationModuleListener
  void on(BookingCancelled event) {
    TenantContext.runAs(
        event.tenantId(),
        () -> projection.offerReleasedHours(event.tutorId(), event.releasedBlockIds()));
  }

  /**
   * Only a student's rating of a tutor moves the tutor's standing. A tutor rating a student changes
   * nothing anybody searches by.
   */
  @ApplicationModuleListener
  void on(SessionRated event) {
    if (event.direction() != SessionRated.Direction.STUDENT_TO_TUTOR) {
      return;
    }
    TenantContext.runAs(
        event.tenantId(),
        () -> projection.refreshStanding(event.ratedUserId(), event.catalogItemId()));
  }
}
