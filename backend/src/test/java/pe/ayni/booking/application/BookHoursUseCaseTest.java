package pe.ayni.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.domain.model.HoldExpired;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourUnavailable;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.InsufficientCreditsException;

/**
 * What happens around the confirmation: a failure never leaves the student's hours held, and never
 * reaches the student in the database's words.
 */
class BookHoursUseCaseTest {

  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID SUBJECT = UUID.randomUUID();
  private static final Instant NINE = Instant.parse("2026-09-21T14:00:00Z");

  private final ConfirmBooking confirmBooking = mock(ConfirmBooking.class);
  private final ReleaseHoldsUseCase releaseHolds = mock(ReleaseHoldsUseCase.class);
  private final BookHoursUseCase bookHours = new BookHoursUseCase(confirmBooking, releaseHolds);

  private void confirmationFailsWith(RuntimeException failure) {
    when(confirmBooking.confirm(STUDENT, TUTOR, SUBJECT, NINE, 2, "Joins")).thenThrow(failure);
  }

  private ConfirmedBooking book() {
    return bookHours.execute(STUDENT, TUTOR, SUBJECT, NINE, 2, "Joins");
  }

  @Test
  @DisplayName("a booking that succeeds releases nothing")
  void aSuccessfulBookingReleasesNothing() {

    ConfirmedBooking confirmed =
        new ConfirmedBooking(
            UUID.randomUUID(), STUDENT, TUTOR, SUBJECT, NINE, NINE.plusSeconds(7200), 2, 2, "Joins",
            BookingStatus.CONFIRMED, List.of());
    when(confirmBooking.confirm(STUDENT, TUTOR, SUBJECT, NINE, 2, "Joins")).thenReturn(confirmed);

    assertThat(book()).isEqualTo(confirmed);
    verifyNoInteractions(releaseHolds);
  }

  @Test
  @DisplayName("a refusal reaches the student unchanged, after the held hours are given back")
  void aRefusalIsRethrownAfterReleasing() {

    InsufficientCreditsException refusal = new InsufficientCreditsException(Credits.of(2));
    confirmationFailsWith(refusal);

    assertThatThrownBy(this::book).isSameAs(refusal);
    verify(releaseHolds).execute(STUDENT, TUTOR, NINE, 2);
  }

  @Test
  @DisplayName("an expired hold is refused, and whatever is left of it is given back")
  void anExpiredHoldIsReleased() {

    confirmationFailsWith(new HoldExpired("gone"));

    assertThatThrownBy(this::book).isInstanceOf(HoldExpired.class);
    verify(releaseHolds).execute(STUDENT, TUTOR, NINE, 2);
  }

  @Test
  @DisplayName("losing the race on the version column is told as an hour taken by someone else")
  void aLostRaceBecomesHourUnavailable() {

    ObjectOptimisticLockingFailureException lostRace =
        new ObjectOptimisticLockingFailureException(HourBlock.class, UUID.randomUUID());
    confirmationFailsWith(lostRace);

    assertThatThrownBy(this::book)
        .isInstanceOf(HourUnavailable.class)
        .hasMessage(HourUnavailable.TAKEN_WHILE_CONFIRMING)
        .hasCause(lostRace);
    verify(releaseHolds).execute(STUDENT, TUTOR, NINE, 2);
  }

  @Test
  @DisplayName("an unexpected failure is reported as a failed booking that charged nothing")
  void anUnexpectedFailureBecomesBookingFailed() {

    DataIntegrityViolationException broken = new DataIntegrityViolationException("broken");
    confirmationFailsWith(broken);

    assertThatThrownBy(this::book)
        .isInstanceOf(BookingFailed.class)
        .hasCause(broken)
        .hasMessageContaining("Nothing was charged and the hours you held are free again");
    verify(releaseHolds).execute(STUDENT, TUTOR, NINE, 2);
  }

  @Test
  @DisplayName("a release that fails does not hide the failure that caused it")
  void aFailingReleaseDoesNotHideTheOriginalFailure() {

    DataIntegrityViolationException broken = new DataIntegrityViolationException("broken");
    confirmationFailsWith(broken);
    IllegalStateException releaseFailure = new IllegalStateException("database gone");
    when(releaseHolds.execute(any(), any(), any(), anyInt())).thenThrow(releaseFailure);

    assertThatThrownBy(this::book)
        .isInstanceOf(BookingFailed.class)
        .hasCause(broken)
        .hasMessageContaining("will be free again within five minutes");
    assertThat(broken.getSuppressed()).containsExactly(releaseFailure);
  }
}
