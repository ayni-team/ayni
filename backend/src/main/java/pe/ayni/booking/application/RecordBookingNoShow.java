package pe.ayni.booking.application;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.ReliabilityIncidentKind;
import pe.ayni.booking.domain.model.TutorReliabilityIncident;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.booking.infrastructure.TutorReliabilityIncidentRepository;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US06 and US09: a session abandoned because a participant did not check in marks its booking
 * {@code NO_SHOW}, and records the absence against the tutor when the student did attend, which
 * means the tutor was the one missing.
 *
 * <p>Nobody was wronged when both were absent, and a tutor who came is not to blame for an absent
 * student, so the booking is marked and nothing is held against the tutor. The incident goes to
 * {@code booking.tutor_reliability} with the same kind a late cancellation uses, and {@code
 * uq_tutor_reliability_booking_kind} stands behind the check here.
 *
 * <p>The booking is locked while this runs, so the same event delivered twice, or at once, records
 * one incident.
 */
@Service
class RecordBookingNoShow {

  private final BookingRepository bookings;
  private final TutorReliabilityIncidentRepository incidents;
  private final Clock clock;

  RecordBookingNoShow(
      BookingRepository bookings, TutorReliabilityIncidentRepository incidents, Clock clock) {
    this.bookings = bookings;
    this.incidents = incidents;
    this.clock = clock;
  }

  @Transactional
  void record(SessionAbandoned event) {
    String tenantId = TenantContext.require();
    UUID bookingId = event.bookingId();
    Booking booking =
        bookings
            .lockByTenantIdAndId(tenantId, bookingId)
            .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));
    if (booking.getStatus() != BookingStatus.CONFIRMED
        && booking.getStatus() != BookingStatus.NO_SHOW) {
      // Cancelled or completed in the meantime: the absence changes nothing about it.
      return;
    }

    booking.markNoShow(clock.instant());

    if (event.studentCheckedIn()
        && !incidents.existsByTenantIdAndBookingIdAndKind(
            tenantId, bookingId, ReliabilityIncidentKind.NO_SHOW)) {
      incidents.save(
          new TutorReliabilityIncident(
              UUID.randomUUID(),
              tenantId,
              booking.getTutorId(),
              bookingId,
              ReliabilityIncidentKind.NO_SHOW,
              event.occurredOn()));
    }
  }
}
