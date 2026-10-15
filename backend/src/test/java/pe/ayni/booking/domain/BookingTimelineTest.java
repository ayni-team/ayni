package pe.ayni.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.BookingTimeline;
import pe.ayni.booking.domain.model.CancelledBy;

/** Which half of the lists of US04 and US21 a booking is in, and when it can be entered. */
class BookingTimelineTest {

  private static final Instant START = Instant.parse("2026-10-16T14:00:00Z");

  private static Booking booking(int hours) {
    return Booking.confirm(
        UUID.randomUUID(),
        "UPC",
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        START,
        START.plus(Duration.ofHours(hours)),
        hours,
        "Normal forms",
        START.minus(Duration.ofDays(2)));
  }

  @Test
  @DisplayName("A confirmed booking is upcoming until its last hour ends")
  void upcomingUntilItsLastHourEnds() {
    Booking booking = booking(2);

    assertThat(booking.isUpcomingAt(START.minus(Duration.ofDays(1)))).isTrue();
    assertThat(booking.isUpcomingAt(START.plus(Duration.ofMinutes(90)))).isTrue();
    assertThat(booking.isUpcomingAt(START.plus(Duration.ofHours(2)))).isFalse();
  }

  @Test
  @DisplayName("A cancelled booking belongs to the history even before its hour")
  void aCancelledBookingIsHistory() {
    Booking booking = booking(1);
    booking.cancel(CancelledBy.STUDENT, "Plans changed", START.minus(Duration.ofDays(1)));

    assertThat(booking.isUpcomingAt(START.minus(Duration.ofHours(20)))).isFalse();
    assertThat(booking.isJoinableAt(START)).isFalse();
  }

  @Test
  @DisplayName("It can be entered from fifteen minutes before the start until the end")
  void joinableWindow() {
    Booking booking = booking(1);

    assertThat(booking.isJoinableAt(START.minus(Duration.ofMinutes(16)))).isFalse();
    assertThat(booking.isJoinableAt(START.minus(Duration.ofMinutes(15)))).isTrue();
    assertThat(booking.isJoinableAt(START.plus(Duration.ofMinutes(59)))).isTrue();
    assertThat(booking.isJoinableAt(START.plus(Duration.ofHours(1)))).isFalse();
  }

  @Test
  @DisplayName("The view is UPCOMING by default, read in any case, and nothing else")
  void namedView() {
    assertThat(BookingTimeline.named(null)).isEqualTo(BookingTimeline.UPCOMING);
    assertThat(BookingTimeline.named(" ")).isEqualTo(BookingTimeline.UPCOMING);
    assertThat(BookingTimeline.named("history")).isEqualTo(BookingTimeline.HISTORY);
    assertThatThrownBy(() -> BookingTimeline.named("PAST")).isInstanceOf(BookingRuleViolation.class);
  }
}
