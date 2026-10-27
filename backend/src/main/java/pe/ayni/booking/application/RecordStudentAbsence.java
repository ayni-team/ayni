package pe.ayni.booking.application;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.StudentAbsence;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.booking.infrastructure.StudentAbsenceRepository;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US34: a session abandoned because the student did not check in records the absence in {@code
 * booking.student_absences}, so the tutor of a later booking is told how many times it happened.
 *
 * <p>The student is absent whether or not the tutor came, and a student who came is never recorded:
 * when only the tutor was missing, the absence is the tutor's and {@link RecordBookingNoShow}
 * writes it down.
 *
 * <p>The booking is locked while this runs, as in {@link RecordBookingNoShow}, so the same event
 * delivered twice, or at once, records one absence; {@code uq_student_absences_booking} stands
 * behind the check here.
 */
@Service
class RecordStudentAbsence {

  private final BookingRepository bookings;
  private final StudentAbsenceRepository absences;
  private final Clock clock;

  RecordStudentAbsence(
      BookingRepository bookings, StudentAbsenceRepository absences, Clock clock) {
    this.bookings = bookings;
    this.absences = absences;
    this.clock = clock;
  }

  @Transactional
  void record(SessionAbandoned event) {
    if (event.studentCheckedIn()) {
      return;
    }
    String tenantId = TenantContext.require();
    UUID bookingId = event.bookingId();
    Booking booking =
        bookings
            .lockByTenantIdAndId(tenantId, bookingId)
            .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));
    if (booking.getStatus() != BookingStatus.CONFIRMED
        && booking.getStatus() != BookingStatus.NO_SHOW) {
      // Cancelled or completed in the meantime: nobody was absent from a session that took place.
      return;
    }

    if (!absences.existsByTenantIdAndBookingId(tenantId, bookingId)) {
      absences.save(
          new StudentAbsence(
              UUID.randomUUID(), tenantId, booking.getStudentId(), bookingId, clock.instant()));
    }
  }
}
