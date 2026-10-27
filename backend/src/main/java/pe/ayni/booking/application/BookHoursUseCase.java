package pe.ayni.booking.application;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.HourUnavailable;
import pe.ayni.wallet.InsufficientCreditsException;

/**
 * US03: a student books the hours they are holding.
 *
 * <p>The confirmation itself is {@link ConfirmBooking}, one transaction. This class has none on
 * purpose, because what it does happens after that transaction is over: when the confirmation
 * fails, it rolls back whole, so the balance is as it was, and then a second transaction gives back
 * the hours the student was holding, so they return to the search at once instead of five minutes
 * later. Doing that inside the failed transaction is impossible: the refusal has already marked it
 * for rollback.
 */
@Service
public class BookHoursUseCase {

  private static final Logger log = LoggerFactory.getLogger(BookHoursUseCase.class);

  private final ConfirmBooking confirmBooking;
  private final ReleaseHoldsUseCase releaseHolds;

  BookHoursUseCase(ConfirmBooking confirmBooking, ReleaseHoldsUseCase releaseHolds) {
    this.confirmBooking = confirmBooking;
    this.releaseHolds = releaseHolds;
  }

  /**
   * @param studentId who books, always the person making the request
   * @param catalogItemId the subject the session is about
   * @param start the start of the first hour
   * @param hours how many consecutive hours, which is also how many credits
   * @param needDescription what the student needs help with, which the tutor reads beforehand
   * @throws BookingRuleViolation when a rule refuses the booking; {@link HourUnavailable} also when
   *     another student confirmed the same hour first
   * @throws InsufficientCreditsException when the balance does not cover the hours
   * @throws NoSuchElementException when the subject is not visible to the student's university
   * @throws BookingFailed when anything else went wrong
   */
  public ConfirmedBooking execute(
      UUID studentId,
      UUID tutorId,
      UUID catalogItemId,
      Instant start,
      int hours,
      String needDescription) {
    try {
      return confirmBooking.confirm(
          studentId, tutorId, catalogItemId, start, hours, needDescription);
    } catch (OptimisticLockingFailureException lostRace) {
      // The version column refused the write: another student took one of these hours after this
      // confirmation read them. Said in the student's terms, not the database's.
      giveBackHeldHours(studentId, tutorId, start, hours, lostRace);
      throw new HourUnavailable(HourUnavailable.TAKEN_WHILE_CONFIRMING, lostRace);
    } catch (BookingRuleViolation | InsufficientCreditsException | NoSuchElementException refusal) {
      giveBackHeldHours(studentId, tutorId, start, hours, refusal);
      throw refusal;
    } catch (RuntimeException failure) {
      log.error("Confirming a booking of {} with {} failed", studentId, tutorId, failure);
      boolean released = giveBackHeldHours(studentId, tutorId, start, hours, failure);
      throw new BookingFailed(failure, released);
    }
  }

  /**
   * Releases what the student held in that stretch, in a transaction of its own.
   *
   * <p>A failure here must not hide the one that brought us here: it is attached to it and the
   * holds are left to run out on their own.
   *
   * @return whether the holds were released
   */
  private boolean giveBackHeldHours(
      UUID studentId, UUID tutorId, Instant start, int hours, RuntimeException original) {
    try {
      releaseHolds.execute(studentId, tutorId, start, hours);
      return true;
    } catch (RuntimeException releaseFailure) {
      original.addSuppressed(releaseFailure);
      log.warn("Could not give back the hours {} held with {}", studentId, tutorId, releaseFailure);
      return false;
    }
  }
}
