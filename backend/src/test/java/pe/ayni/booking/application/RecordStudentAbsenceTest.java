package pe.ayni.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.CancelledBy;
import pe.ayni.booking.domain.model.StudentAbsence;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.booking.infrastructure.StudentAbsenceRepository;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

class RecordStudentAbsenceTest {

  private static final String TENANT = "UPC";
  private static final Instant NOW = Instant.parse("2026-09-30T20:10:00Z");
  private static final UUID BOOKING_ID = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();

  private final BookingRepository bookings = mock(BookingRepository.class);
  private final StudentAbsenceRepository absences = mock(StudentAbsenceRepository.class);
  private final RecordStudentAbsence useCase =
      new RecordStudentAbsence(bookings, absences, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("records the absence of the student when the student did not check in")
  void recordsTheAbsence() {
    Booking booking = booking();
    when(bookings.lockByTenantIdAndId(TENANT, BOOKING_ID)).thenReturn(Optional.of(booking));

    TenantContext.runAs(TENANT, () -> useCase.record(abandoned(false)));

    ArgumentCaptor<StudentAbsence> saved = ArgumentCaptor.forClass(StudentAbsence.class);
    verify(absences).save(saved.capture());
    assertThat(saved.getValue())
        .extracting("tenantId", "studentId", "bookingId", "recordedAt")
        .containsExactly(TENANT, STUDENT, BOOKING_ID, NOW);
  }

  @Test
  @DisplayName("records nothing when the student came and the tutor was the one missing")
  void recordsNothingWhenTheStudentCame() {
    TenantContext.runAs(TENANT, () -> useCase.record(abandoned(true)));

    verify(absences, never()).save(any());
  }

  @Test
  @DisplayName("an event delivered again records no second absence")
  void aRepeatedEventRecordsNothing() {
    when(bookings.lockByTenantIdAndId(TENANT, BOOKING_ID)).thenReturn(Optional.of(booking()));
    when(absences.existsByTenantIdAndBookingId(TENANT, BOOKING_ID)).thenReturn(true);

    TenantContext.runAs(TENANT, () -> useCase.record(abandoned(false)));

    verify(absences, never()).save(any());
  }

  @Test
  @DisplayName("a booking cancelled in the meantime records no absence")
  void aCancelledBookingRecordsNothing() {
    Booking booking = booking();
    booking.cancel(CancelledBy.STUDENT, "", NOW.minus(Duration.ofHours(20)));
    when(bookings.lockByTenantIdAndId(TENANT, BOOKING_ID)).thenReturn(Optional.of(booking));

    TenantContext.runAs(TENANT, () -> useCase.record(abandoned(false)));

    verify(absences, never()).save(any());
  }

  private static SessionAbandoned abandoned(boolean studentCheckedIn) {
    return new SessionAbandoned(
        TENANT,
        UUID.randomUUID(),
        BOOKING_ID,
        TUTOR,
        STUDENT,
        UUID.randomUUID(),
        studentCheckedIn,
        NOW);
  }

  private static Booking booking() {
    Instant start = NOW.minus(Duration.ofMinutes(10));
    return Booking.confirm(
        BOOKING_ID,
        TENANT,
        STUDENT,
        TUTOR,
        UUID.randomUUID(),
        start,
        start.plus(Duration.ofHours(1)),
        1,
        "Algebra",
        NOW.minus(Duration.ofDays(2)));
  }
}
