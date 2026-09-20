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
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;

/** What one hour of a tutor's time allows to be done to it. */
class HourBlockTest {

  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID ANOTHER_STUDENT = UUID.randomUUID();
  private static final UUID BOOKING = UUID.randomUUID();

  private static HourBlock freeBlock() {
    return new HourBlock(
        UUID.randomUUID(), UPC, TUTOR, NOW, NOW.plus(Duration.ofHours(1)), UUID.randomUUID(), NOW);
  }

  @Test
  @DisplayName("a block is born free")
  void startsAvailable() {
    assertThat(freeBlock().getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("holding it records who holds it and until when")
  void holdingRecordsTheHolderAndTheDeadline() {

    HourBlock block = freeBlock();
    block.hold(STUDENT, NOW);

    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.HELD);
    assertThat(block.getHeldBy()).isEqualTo(STUDENT);
    assertThat(block.getHeldUntil()).isEqualTo(NOW.plus(HourBlock.DEFAULT_HOLD_DURATION));
  }

  @Test
  @DisplayName("an hour somebody else is still holding cannot be taken")
  void refusesToTakeALiveHold() {

    HourBlock block = freeBlock();
    block.hold(STUDENT, NOW);

    assertThatThrownBy(() -> block.hold(ANOTHER_STUDENT, NOW.plus(Duration.ofMinutes(5))))
        .isInstanceOf(BookingRuleViolation.class);
    assertThat(block.getHeldBy()).isEqualTo(STUDENT);
  }

  @Test
  @DisplayName("an abandoned hold can be taken over without waiting for the sweep")
  void anExpiredHoldCanBeTakenOver() {

    HourBlock block = freeBlock();
    block.hold(STUDENT, NOW);

    Instant afterTheHold = NOW.plus(HourBlock.DEFAULT_HOLD_DURATION).plusSeconds(1);
    block.hold(ANOTHER_STUDENT, afterTheHold);

    assertThat(block.getHeldBy()).isEqualTo(ANOTHER_STUDENT);
  }

  @Test
  @DisplayName("the sweep returns an expired hold to circulation")
  void releasesAnExpiredHold() {

    HourBlock block = freeBlock();
    block.hold(STUDENT, NOW);

    block.releaseHold(NOW.plus(HourBlock.DEFAULT_HOLD_DURATION).plusSeconds(1));

    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(block.getHeldBy()).isNull();
    assertThat(block.getHeldUntil()).isNull();
  }

  @Test
  @DisplayName("the sweep refuses to take an hour from somebody still deciding")
  void refusesToReleaseALiveHold() {

    HourBlock block = freeBlock();
    block.hold(STUDENT, NOW);

    assertThatThrownBy(() -> block.releaseHold(NOW.plus(Duration.ofMinutes(1))))
        .isInstanceOf(BookingRuleViolation.class);
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.HELD);
  }

  @Test
  @DisplayName("the sweep leaves a confirmed hour alone")
  void releasingAHoldLeavesABookedBlockAlone() {

    HourBlock block = freeBlock();
    block.book(BOOKING);

    // A sweep working from a list that went stale must not turn a confirmed hour back into a
    // free one.
    block.releaseHold(NOW.plus(Duration.ofDays(1)));

    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.BOOKED);
  }

  @Test
  @DisplayName("confirming clears the hold and points at the booking")
  void bookingClearsTheHold() {

    HourBlock block = freeBlock();
    block.hold(STUDENT, NOW);
    block.book(BOOKING);

    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.BOOKED);
    assertThat(block.getBookingId()).isEqualTo(BOOKING);
    assertThat(block.getHeldBy()).isNull();
    assertThat(block.getHeldUntil()).isNull();
  }

  @Test
  @DisplayName("an hour already confirmed cannot be confirmed again")
  void refusesToBookTwice() {

    HourBlock block = freeBlock();
    block.book(BOOKING);

    assertThatThrownBy(() -> block.book(UUID.randomUUID()))
        .isInstanceOf(BookingRuleViolation.class);
  }

  @Test
  @DisplayName("cancelling takes the hour out of circulation")
  void releasingABookedBlock() {

    HourBlock block = freeBlock();
    block.book(BOOKING);
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
