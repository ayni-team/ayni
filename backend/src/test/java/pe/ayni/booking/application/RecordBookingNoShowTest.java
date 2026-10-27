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
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.CancelledBy;
import pe.ayni.booking.domain.model.ReliabilityIncidentKind;
import pe.ayni.booking.domain.model.TutorReliabilityIncident;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.booking.infrastructure.TutorReliabilityIncidentRepository;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

class RecordBookingNoShowTest {

  private static final String TENANT = "UPC";
  private static final Instant NOW = Instant.parse("2026-09-30T20:10:00Z");
  private static final UUID BOOKING_ID = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();

  private final BookingRepository bookings = mock(BookingRepository.class);
  private final TutorReliabilityIncidentRepository incidents =
      mock(TutorReliabilityIncidentRepository.class);
  private final RecordBookingNoShow useCase =
      new RecordBookingNoShow(bookings, incidents, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("marks the booking no-show and records the tutor's incident when the student came")
  void recordsTheNoShowAgainstTheTutor() {
    Booking booking = booking();
    when(bookings.lockByTenantIdAndId(TENANT, BOOKING_ID)).thenReturn(Optional.of(booking));

    TenantContext.runAs(TENANT, () -> useCase.record(abandoned(true)));

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
    assertThat(booking.getUpdatedAt()).isEqualTo(NOW);
    ArgumentCaptor<TutorReliabilityIncident> saved =
        ArgumentCaptor.forClass(TutorReliabilityIncident.class);
    verify(incidents).save(saved.capture());
    assertThat(saved.getValue())
        .extracting("tenantId", "tutorId", "bookingId", "kind", "occurredAt")
        .containsExactly(TENANT, TUTOR, BOOKING_ID, ReliabilityIncidentKind.NO_SHOW, NOW);
  }

  @Test
  @DisplayName("marks the booking no-show and holds nothing against the tutor when both were absent")
  void bothAbsentRecordsNoIncident() {
    Booking booking = booking();
    when(bookings.lockByTenantIdAndId(TENANT, BOOKING_ID)).thenReturn(Optional.of(booking));

    TenantContext.runAs(TENANT, () -> useCase.record(abandoned(false)));

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
    verify(incidents, never()).save(any());
  }

  @Test
  @DisplayName("an event delivered again changes nothing and records no second incident")
  void aRepeatedEventChangesNothing() {
    Booking booking = booking();
    booking.markNoShow(NOW.minusSeconds(30));
    when(bookings.lockByTenantIdAndId(TENANT, BOOKING_ID)).thenReturn(Optional.of(booking));
    when(incidents.existsByTenantIdAndBookingIdAndKind(
            TENANT, BOOKING_ID, ReliabilityIncidentKind.NO_SHOW))
        .thenReturn(true);

    TenantContext.runAs(TENANT, () -> useCase.record(abandoned(true)));

    assertThat(booking.getUpdatedAt()).isEqualTo(NOW.minusSeconds(30));
    verify(incidents, never()).save(any());
  }

  @Test
  @DisplayName("a booking cancelled in the meantime is left as it is")
  void aCancelledBookingIsLeftAlone() {
    Booking booking = booking();
    booking.cancel(CancelledBy.STUDENT, "", NOW.minus(Duration.ofHours(20)));
    when(bookings.lockByTenantIdAndId(TENANT, BOOKING_ID)).thenReturn(Optional.of(booking));

    TenantContext.runAs(TENANT, () -> useCase.record(abandoned(true)));

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    verify(incidents, never()).save(any());
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
