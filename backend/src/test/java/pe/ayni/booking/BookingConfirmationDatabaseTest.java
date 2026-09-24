package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.UnexpectedRollbackException;
import pe.ayni.booking.application.BookHoursUseCase;
import pe.ayni.booking.application.BookingFailed;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;
import pe.ayni.booking.domain.model.HourUnavailable;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.InsufficientCreditsException;

/**
 * The two promises of the confirmation that only a real database can check: two transactions
 * racing for one hour are decided by the version column, and a confirmation that fails after
 * everything was written leaves nothing behind.
 */
class BookingConfirmationDatabaseTest extends BookingScenario {

  @Autowired private BookHoursUseCase bookHours;

  /**
   * Scenario 4 at the worst possible moment, staged step by step rather than left to luck.
   *
   * <p>Ana's confirmation has already read the hour, with its version, and checked that her hold is
   * alive; it is paused inside the charge, before touching anything. Meanwhile her hold runs out,
   * Bruno takes the hour over and books it, and commits. When Ana's confirmation carries on, its
   * update of the hour finds a version that is no longer there: it loses, and the charge it had just
   * made rolls back with it.
   */
  @Test
  @DisplayName("two confirmations racing for one hour: the version column decides, the loser pays nothing")
  void theVersionColumnDecidesARaceAtConfirmation() throws Exception {

    grant(ana, 5);
    grant(bruno, 5);
    hold(ana, tomorrowAt(10), 1).andExpect(status().isCreated());
    clock.advance(Duration.ofMinutes(4).plusSeconds(59));

    CountDownLatch anaIsBeingCharged = new CountDownLatch(1);
    CountDownLatch anaMayContinue = new CountDownLatch(1);
    doAnswer(
            charge -> {
              anaIsBeingCharged.countDown();
              assertThat(anaMayContinue.await(30, TimeUnit.SECONDS)).isTrue();
              return charge.callRealMethod();
            })
        .when(wallet)
        .charge(eq(ana), any(Credits.class), any(UUID.class));

    ExecutorService anasRequest = Executors.newSingleThreadExecutor();
    try {
      Future<MockHttpServletResponse> anasAnswer =
          anasRequest.submit(
              () -> book(ana, tomorrowAt(10), 1, "Recursion").andReturn().getResponse());

      assertThat(anaIsBeingCharged.await(30, TimeUnit.SECONDS)).isTrue();

      // Two seconds later Ana's hold has run out, and Bruno gets there first.
      clock.advance(Duration.ofSeconds(2));
      hold(bruno, tomorrowAt(10), 1).andExpect(status().isCreated());
      String brunosBooking =
          book(bruno, tomorrowAt(10), 1, "Recursion")
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      anaMayContinue.countDown();
      MockHttpServletResponse anasResponse = anasAnswer.get(30, TimeUnit.SECONDS);

      assertThat(anasResponse.getStatus()).isEqualTo(409);
      assertThat(anasResponse.getContentAsString())
          .contains("just taken by another student")
          .contains("Nothing was charged");

      assertThat(balanceOf(ana)).isEqualTo(5);
      assertThat(chargesOf(ana)).isZero();
      assertThat(bookingsOf(ana)).isZero();
      assertThat(balanceOf(bruno)).isEqualTo(4);

      HourBlock block = blockAt(tomorrowAt(10));
      assertThat(block.getStatus()).isEqualTo(HourBlockStatus.BOOKED);
      assertThat(block.getBookingId().toString())
          .isEqualTo(JsonPath.read(brunosBooking, "$.id"));
    } finally {
      anaMayContinue.countDown();
      anasRequest.shutdownNow();
    }
  }

  @Test
  @DisplayName("the lost race reaches the student as HourUnavailable, caused by the version conflict")
  void theLostRaceIsTranslatedForTheStudent() throws Exception {

    grant(ana, 5);
    grant(bruno, 5);
    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());
    clock.advance(Duration.ofMinutes(4).plusSeconds(59));

    CountDownLatch anaIsBeingCharged = new CountDownLatch(1);
    CountDownLatch anaMayContinue = new CountDownLatch(1);
    doAnswer(
            charge -> {
              anaIsBeingCharged.countDown();
              assertThat(anaMayContinue.await(30, TimeUnit.SECONDS)).isTrue();
              return charge.callRealMethod();
            })
        .when(wallet)
        .charge(eq(ana), any(Credits.class), any(UUID.class));

    ExecutorService anasThread = Executors.newSingleThreadExecutor();
    try {
      Future<Throwable> anasFailure =
          anasThread.submit(
              () -> {
                try {
                  TenantContext.runAs(
                      UPC,
                      () -> bookHours.execute(ana, tutor, subject, tomorrowAt(9), 1, "Recursion"));
                  return null;
                } catch (RuntimeException failure) {
                  return failure;
                }
              });

      assertThat(anaIsBeingCharged.await(30, TimeUnit.SECONDS)).isTrue();
      clock.advance(Duration.ofSeconds(2));
      hold(bruno, tomorrowAt(9), 1).andExpect(status().isCreated());
      book(bruno, tomorrowAt(9), 1, "Recursion").andExpect(status().isCreated());
      anaMayContinue.countDown();

      assertThat(anasFailure.get(30, TimeUnit.SECONDS))
          .isInstanceOf(HourUnavailable.class)
          .hasCauseInstanceOf(OptimisticLockingFailureException.class);
    } finally {
      anaMayContinue.countDown();
      anasThread.shutdownNow();
    }
  }

  /**
   * Scenario 6 against the database: the confirmation fails just before committing, when the
   * charge, the hours and the booking have all been flushed. The transaction rolls back whole, and
   * the hold is given back in a second one.
   *
   * <p>Catching the failure inside the confirmation's transaction would have ended in {@code
   * UnexpectedRollbackException}. It must not appear anywhere.
   */
  @Test
  @DisplayName("a failure after everything was written leaves the balance intact and the hour free")
  void aFailureAfterEverythingWasWrittenLeavesNothingBehind() throws Exception {

    grant(ana, 3);
    hold(ana, tomorrowAt(9), 2).andExpect(status().isCreated());
    failingConfirmation.failNextConfirmationOf(ana);

    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    UPC,
                    () -> bookHours.execute(ana, tutor, subject, tomorrowAt(9), 2, "Triggers")))
        .isInstanceOf(BookingFailed.class)
        .hasRootCauseMessage("Simulated failure while committing the booking")
        .satisfies(
            failure -> {
              assertThat(failure).hasNoSuppressedExceptions();
              assertThat(failure).cause().isNotInstanceOf(UnexpectedRollbackException.class);
            });

    assertThat(balanceOf(ana)).isEqualTo(3);
    assertThat(chargesOf(ana)).isZero();
    assertThat(bookingsOf(ana)).isZero();
    for (int hour = 9; hour < 11; hour++) {
      HourBlock block = blockAt(tomorrowAt(hour));
      assertThat(block.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
      assertThat(block.getHeldBy()).isNull();
      assertThat(block.getBookingId()).isNull();
    }
  }

  /**
   * Wallet's refusal marks the confirmation's transaction for rollback. Because nobody catches it
   * inside, the student gets the refusal itself, with the missing credits, and not an unexpected
   * rollback.
   */
  @Test
  @DisplayName("wallet's refusal reaches the student as it is, never as an unexpected rollback")
  void walletsRefusalIsNotAnUnexpectedRollback() throws Exception {

    grant(ana, 1);
    hold(ana, tomorrowAt(9), 3).andExpect(status().isCreated());

    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    UPC,
                    () -> bookHours.execute(ana, tutor, subject, tomorrowAt(9), 3, "Joins")))
        .isInstanceOfSatisfying(
            InsufficientCreditsException.class,
            refusal -> assertThat(refusal.missing()).isEqualTo(Credits.of(2)));

    assertThat(balanceOf(ana)).isEqualTo(1);
    assertThat(blockAt(tomorrowAt(9)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }
}
