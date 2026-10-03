package pe.ayni.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.shared.tenancy.TenantContext;

class RecordBookingNoShowTest {

  private static final String TENANT = "UPC";
  private static final Instant NOW = Instant.parse("2026-09-30T20:10:00Z");
  private static final UUID BOOKING_ID = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();

  @Test
  @DisplayName("marks the confirmed booking as no-show")
  void recordsBookingNoShow() {
    BookingRepository bookings = mock(BookingRepository.class);
    Booking booking = booking();
    when(bookings.lockByTenantIdAndId(TENANT, BOOKING_ID)).thenReturn(Optional.of(booking));
    RecordBookingNoShow useCase =
        new RecordBookingNoShow(bookings, Clock.fixed(NOW, ZoneOffset.UTC));

    TenantContext.runAs(TENANT, () -> useCase.record(BOOKING_ID));

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
    assertThat(booking.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("does not rewrite an already recorded no-show")
  void keepsExistingNoShowTimestamp() {
    BookingRepository bookings = mock(BookingRepository.class);
    Booking booking = booking();
    booking.markNoShow(NOW.minusSeconds(30));
    when(bookings.lockByTenantIdAndId(TENANT, BOOKING_ID)).thenReturn(Optional.of(booking));
    RecordBookingNoShow useCase =
        new RecordBookingNoShow(bookings, Clock.fixed(NOW, ZoneOffset.UTC));

    TenantContext.runAs(TENANT, () -> useCase.record(BOOKING_ID));

    assertThat(booking.getUpdatedAt()).isEqualTo(NOW.minusSeconds(30));
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
        NOW.minus(Duration.ofHours(1)));
  }
}
