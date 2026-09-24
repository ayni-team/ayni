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
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.HoldExpired;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;
import pe.ayni.booking.domain.model.HourUnavailable;

/** What one hour of a tutor's time allows to be done to it. */
class HourBlockTest {

  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID ANOTHER_STUDENT = UUID.randomUUID();
  private static final UUID BOOKING = UUID.randomUUID();

  /** Tomorrow at the same time, so that holding and booking it now is allowed. */
  private static final Instant STARTS_AT = NOW.plus(Duration.ofDays(1));

  private static final Instant JUST_AFTER_THE_HOLD =
      NOW.plus(HourBlock.HOLD_DURATION).plusSeconds(1);

  private static HourBlock freeBlock() {
    return new HourBlock(
        UUID.randomUUID(),
        UPC,
        TUTOR,
        STARTS_AT,
        STARTS_AT.plus(Duration.ofHours(1)),
        UUID.randomUUID(),
        NOW);
  }

  private static HourBlock heldBy(UUID student) {
    HourBlock block = freeBlock();
    block.hold(student, NOW);
    return block;
  }

  @Test
  @DisplayName("a block is born free")
  void startsAvailable() {
    assertThat(freeBlock().getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("holding it records who holds it and for five minutes")
  void holdingRecordsTheHolderAndTheDeadline() {

    HourBlock block = heldBy(STUDENT);

    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.HELD);
    assertThat(block.getHeldBy()).isEqualTo(STUDENT);
    assertThat(HourBlock.HOLD_DURATION).isEqualTo(Duration.ofMinutes(5));
    assertThat(block.getHeldUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
  }

  @Test
  @DisplayName("an hour somebody else is still holding cannot be taken")
  void refusesToTakeALiveHold() {

    HourBlock block = heldBy(STUDENT);

    assertThatThrownBy(() -> block.hold(ANOTHER_STUDENT, NOW.plus(Duration.ofMinutes(4))))
        .isInstanceOf(HourUnavailable.class)
        .hasMessageContaining("Another student is holding this hour");
    assertThat(block.getHeldBy()).isEqualTo(STUDENT);
  }

  @Test
  @DisplayName("a hold is still alive at the very instant it ends")
  void aHoldIsAliveUntilItsLastInstant() {

    HourBlock block = heldBy(STUDENT);

    assertThatThrownBy(() -> block.hold(ANOTHER_STUDENT, NOW.plus(HourBlock.HOLD_DURATION)))
        .isInstanceOf(HourUnavailable.class);
  }

  @Test
  @DisplayName("an abandoned hold can be taken over without waiting for the sweep")
  void anExpiredHoldCanBeTakenOver() {

    HourBlock block = heldBy(STUDENT);
    block.hold(ANOTHER_STUDENT, JUST_AFTER_THE_HOLD);

    assertThat(block.getHeldBy()).isEqualTo(ANOTHER_STUDENT);
    assertThat(block.getHeldUntil()).isEqualTo(JUST_AFTER_THE_HOLD.plus(HourBlock.HOLD_DURATION));
  }

  @Test
  @DisplayName("holding again what you already hold does not extend it")
  void holdingAgainDoesNotExtendTheHold() {

    HourBlock block = heldBy(STUDENT);
    block.hold(STUDENT, NOW.plus(Duration.ofMinutes(3)));

    assertThat(block.getHeldUntil()).isEqualTo(NOW.plus(HourBlock.HOLD_DURATION));
  }

  @Test
  @DisplayName("an hour that has started cannot be held")
  void refusesToHoldAStartedHour() {
    assertThatThrownBy(() -> freeBlock().hold(STUDENT, STARTS_AT))
        .isInstanceOf(HourUnavailable.class)
        .hasMessageContaining("already started");
  }

  @Test
  @DisplayName("a booked hour cannot be held")
  void refusesToHoldABookedHour() {

    HourBlock block = heldBy(STUDENT);
    block.book(BOOKING, STUDENT, NOW);

    assertThatThrownBy(() -> block.hold(ANOTHER_STUDENT, JUST_AFTER_THE_HOLD))
        .isInstanceOf(HourUnavailable.class)
        .hasMessageContaining("already booked");
  }

  @Test
  @DisplayName("the sweep returns an expired hold to circulation")
  void releasesAnExpiredHold() {

    HourBlock block = heldBy(STUDENT);
    block.releaseHold(JUST_AFTER_THE_HOLD);

    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(block.getHeldBy()).isNull();
    assertThat(block.getHeldUntil()).isNull();
  }

  @Test
  @DisplayName("the sweep refuses to take an hour from somebody still deciding")
  void refusesToReleaseALiveHold() {

    HourBlock block = heldBy(STUDENT);

    assertThatThrownBy(() -> block.releaseHold(NOW.plus(Duration.ofMinutes(1))))
        .isInstanceOf(BookingRuleViolation.class);
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.HELD);
  }

  @Test
  @DisplayName("the sweep leaves a confirmed hour alone")
  void releasingAHoldLeavesABookedBlockAlone() {

    HourBlock block = heldBy(STUDENT);
    block.book(BOOKING, STUDENT, NOW);

    // A sweep working from a list that went stale must not turn a confirmed hour back into a
    // free one.
    block.releaseHold(NOW.plus(Duration.ofDays(1)));

    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.BOOKED);
  }

  @Test
  @DisplayName("a student can give back the hour they hold")
  void aStudentGivesBackTheirHold() {

    HourBlock block = heldBy(STUDENT);

    assertThat(block.releaseHoldOf(STUDENT)).isTrue();
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(block.getHeldBy()).isNull();
    assertThat(block.getHeldUntil()).isNull();
  }

  @Test
  @DisplayName("giving back only touches your own hold")
  void givingBackLeavesOtherPeoplesHoursAlone() {

    HourBlock heldByAnother = heldBy(ANOTHER_STUDENT);
    HourBlock booked = heldBy(STUDENT);
    booked.book(BOOKING, STUDENT, NOW);
    HourBlock free = freeBlock();

    assertThat(heldByAnother.releaseHoldOf(STUDENT)).isFalse();
    assertThat(heldByAnother.getHeldBy()).isEqualTo(ANOTHER_STUDENT);
    assertThat(booked.releaseHoldOf(STUDENT)).isFalse();
    assertThat(booked.getStatus()).isEqualTo(HourBlockStatus.BOOKED);
    assertThat(free.releaseHoldOf(STUDENT)).isFalse();
    assertThat(free.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("confirming turns the student's hold into the booking")
  void bookingClearsTheHold() {

    HourBlock block = heldBy(STUDENT);
    block.book(BOOKING, STUDENT, NOW.plus(Duration.ofMinutes(4)));

    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.BOOKED);
    assertThat(block.getBookingId()).isEqualTo(BOOKING);
    assertThat(block.getHeldBy()).isNull();
    assertThat(block.getHeldUntil()).isNull();
  }

  @Test
  @DisplayName("a free hour cannot be confirmed without holding it first")
  void refusesToBookWithoutAHold() {
    assertThatThrownBy(() -> freeBlock().book(BOOKING, STUDENT, NOW))
        .isInstanceOf(HoldExpired.class);
  }

  @Test
  @DisplayName("a hold that ran out cannot be confirmed")
  void refusesToBookAnExpiredHold() {

    HourBlock block = heldBy(STUDENT);

    assertThatThrownBy(() -> block.book(BOOKING, STUDENT, JUST_AFTER_THE_HOLD))
        .isInstanceOf(HoldExpired.class)
        .hasMessageContaining("five minutes");
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.HELD);
  }

  @Test
  @DisplayName("an hour another student holds cannot be confirmed")
  void refusesToBookSomebodyElsesHold() {

    HourBlock block = heldBy(ANOTHER_STUDENT);

    assertThatThrownBy(() -> block.book(BOOKING, STUDENT, NOW))
        .isInstanceOf(HourUnavailable.class);
    assertThat(block.getHeldBy()).isEqualTo(ANOTHER_STUDENT);
  }

  @Test
  @DisplayName("an hour already confirmed cannot be confirmed again")
  void refusesToBookTwice() {

    HourBlock block = heldBy(STUDENT);
    block.book(BOOKING, STUDENT, NOW);

    assertThatThrownBy(() -> block.book(UUID.randomUUID(), STUDENT, NOW))
        .isInstanceOf(HourUnavailable.class)
        .hasMessageContaining("already booked");
  }

  @Test
  @DisplayName("an hour that has started cannot be confirmed, even with a hold")
  void refusesToBookAStartedHour() {

    HourBlock block = new HourBlock(
        UUID.randomUUID(), UPC, TUTOR, NOW.plus(Duration.ofMinutes(2)),
        NOW.plus(Duration.ofMinutes(62)), null, NOW);
    block.hold(STUDENT, NOW);

    assertThatThrownBy(() -> block.book(BOOKING, STUDENT, NOW.plus(Duration.ofMinutes(3))))
        .isInstanceOf(HourUnavailable.class)
        .hasMessageContaining("already started");
  }

  @Test
  @DisplayName("cancelling takes the hour out of circulation")
  void releasingABookedBlock() {

    HourBlock block = heldBy(STUDENT);
    block.book(BOOKING, STUDENT, NOW);
    block.release();

    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.RELEASED);
    assertThat(block.getBookingId()).isNull();
  }

  @Test
  @DisplayName("only a confirmed hour can be released")
  void refusesToReleaseWhatWasNotBooked() {
    assertThatThrownBy(() -> freeBlock().release()).isInstanceOf(BookingRuleViolation.class);
  }

  @Test
  @DisplayName("an hour that ends before it starts is refused")
  void refusesABackwardsHour() {
    assertThatThrownBy(
            () ->
                new HourBlock(
                    UUID.randomUUID(), UPC, TUTOR, NOW, NOW.minusSeconds(1), null, NOW))
        .isInstanceOf(BookingRuleViolation.class);
  }
}
