package pe.ayni.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.booking.domain.BookingFixtures.NOW;
import static pe.ayni.booking.domain.BookingFixtures.TUTOR;
import static pe.ayni.booking.domain.BookingFixtures.UPC;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.CancelledBy;
import pe.ayni.booking.domain.model.NotBookingParticipant;
import pe.ayni.shared.domain.Credits;

/** What a booking has to be true about from the moment it exists. */
class BookingTest {

  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID ITEM = UUID.randomUUID();
  private static final Instant TOMORROW_AT_NINE = Instant.parse("2026-09-21T14:00:00Z");

  private static Booking booking(UUID student, int hours, String need) {
    return Booking.confirm(
        UUID.randomUUID(),
        UPC,
        student,
        TUTOR,
        ITEM,
        TOMORROW_AT_NINE,
        TOMORROW_AT_NINE.plus(Duration.ofHours(Math.max(hours, 1))),
        hours,
        need,
        NOW);
  }

  @Test
  @DisplayName("a booking is born confirmed and costs one credit per hour")
  void costsOneCreditPerHour() {

    Booking booking = booking(STUDENT, 3, "Normal forms before the exam");

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(booking.getHours()).isEqualTo(3);
    assertThat(booking.getCreditsCharged()).isEqualTo(3);
    assertThat(booking.price()).isEqualTo(Credits.of(3));
    assertThat(booking.getCreatedAt()).isEqualTo(NOW);
    assertThat(booking.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("the need description is kept, without surrounding blanks")
  void keepsTheNeedDescription() {
    assertThat(booking(STUDENT, 1, "  Recursion in Python \n").getNeedDescription())
        .isEqualTo("Recursion in Python");
  }

  @Test
  @DisplayName("a booking without a need description is refused")
  void refusesAnUndescribedNeed() {
    assertThatThrownBy(() -> booking(STUDENT, 1, "   "))
        .isInstanceOf(BookingRuleViolation.class)
        .hasMessageContaining("what you need");
  }

  @Test
  @DisplayName("a tutor cannot book their own hours")
  void refusesBookingYourself() {
    assertThatThrownBy(() -> booking(TUTOR, 1, "Anything"))
        .isInstanceOf(BookingRuleViolation.class)
        .hasMessageContaining("own hours");
  }

  @Test
  @DisplayName("a booking covers at least one hour")
  void refusesZeroHours() {
    assertThatThrownBy(() -> booking(STUDENT, 0, "Anything"))
        .isInstanceOf(BookingRuleViolation.class);
  }

  @Test
  @DisplayName("a booking that ends before it starts is refused")
  void refusesABackwardsRange() {
    assertThatThrownBy(
            () ->
                Booking.confirm(
                    UUID.randomUUID(), UPC, STUDENT, TUTOR, ITEM, TOMORROW_AT_NINE,
                    TOMORROW_AT_NINE, 1, "Anything", NOW))
        .isInstanceOf(BookingRuleViolation.class);
  }

  @Test
  @DisplayName("a person is the student or the tutor of a booking, or neither")
  void knowsWhichSideAPersonIs() {
    Booking booking = booking(STUDENT, 1, "Anything");

    assertThat(booking.participantRole(STUDENT)).isEqualTo(CancelledBy.STUDENT);
    assertThat(booking.participantRole(TUTOR)).isEqualTo(CancelledBy.TUTOR);
    assertThatThrownBy(() -> booking.participantRole(UUID.randomUUID()))
        .isInstanceOf(NotBookingParticipant.class);
  }

  @Test
  @DisplayName("twelve hours before the start is on time and one second less is late")
  void lateIsStrictlyLessThanTwelveHours() {
    Booking booking = booking(STUDENT, 1, "Anything");

    assertThat(booking.isLateToCancelAt(TOMORROW_AT_NINE.minus(Duration.ofHours(12)))).isFalse();
    assertThat(
            booking.isLateToCancelAt(
                TOMORROW_AT_NINE.minus(Duration.ofHours(12)).plusSeconds(1)))
        .isTrue();
  }

  @Test
  @DisplayName("the student is refunded unless they cancel late, the tutor's cancellation always")
  void refundsFollowTheCancellationRule() {
    Booking booking = booking(STUDENT, 1, "Anything");
    Instant onTime = TOMORROW_AT_NINE.minus(Duration.ofHours(13));
    Instant late = TOMORROW_AT_NINE.minus(Duration.ofHours(2));

    assertThat(booking.refundsWhenCancelledBy(CancelledBy.STUDENT, onTime)).isTrue();
    assertThat(booking.refundsWhenCancelledBy(CancelledBy.STUDENT, late)).isFalse();
    assertThat(booking.refundsWhenCancelledBy(CancelledBy.TUTOR, onTime)).isTrue();
    assertThat(booking.refundsWhenCancelledBy(CancelledBy.TUTOR, late)).isTrue();
  }
}
