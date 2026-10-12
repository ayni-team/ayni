package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import pe.ayni.booking.BookingTestConfig.CancelledEvents;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.events.BookingCancelled;

/**
 * US05 over HTTP against a real PostgreSQL, one test per scenario of {@code
 * features/US05-cancel-booking.feature}, with the display name of the scenario.
 *
 * <p>Every booking is made the real way, holding and confirming over HTTP, and then the clock is
 * moved to where the scenario needs it: the rule is about how far the start is from now, and the
 * test decides what now is.
 */
class CancelBookingAcceptanceTest extends BookingScenario {

  private static final int HOUR = 9;
  private static final Duration AHEAD = Duration.ofHours(13);
  private static final Duration LATE = Duration.ofHours(11);

  private final UUID carla = UUID.randomUUID();

  /**
   * The hour of the booking, taken before any test moves the clock: {@code tomorrowAt} reads the
   * clock, and a clock moved to the start would make tomorrow the day after.
   */
  private Instant start;

  @BeforeEach
  void pinTheStart() {
    start = tomorrowAt(HOUR);
  }

  @Autowired private CancelledEvents cancelled;

  @Test
  @DisplayName("Cancellation at least twelve hours ahead returns the credits")
  void cancellationAheadOfTimeReturnsTheCredits() throws Exception {

    Instant seedExpiry = start.plus(Duration.ofDays(10));
    UUID bookingId = aBookingOfAna(seedExpiry);
    List<Map<String, Object>> lotsBefore = lotsOf(ana);
    assertThat(balanceOf(ana)).isEqualTo(4);

    clock.set(start.minus(AHEAD));
    cancel(ana, bookingId, false).andExpect(status().isNoContent());

    assertThat(bookingRow(bookingId))
        .containsEntry("status", "CANCELLED")
        .containsEntry("cancelled_by", "STUDENT")
        .containsEntry("cancelled_late", false);
    assertThat(sessionStatus(bookingId)).isEqualTo("CANCELLED");
    assertThat(balanceOf(ana)).isEqualTo(5);
    assertThat(refundsOf(bookingId)).isEqualTo(1);
    // The credit went back to the group it came from, with the expiry that group had.
    assertThat(lotsOf(ana)).hasSize(lotsBefore.size());
    assertThat(lotsOf(ana).stream().filter(lot -> "SEED".equals(lot.get("credit_type"))))
        .singleElement()
        .satisfies(
            lot -> {
              assertThat(lot.get("remaining_amount")).isEqualTo(3);
              assertThat(((java.sql.Timestamp) lot.get("expires_at")).toInstant())
                  .isEqualTo(seedExpiry);
            });
    assertThat(blockAt(start).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(cancelled.of(bookingId))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.cancelledBy()).isEqualTo(BookingCancelled.CancelledBy.STUDENT);
              assertThat(event.late()).isFalse();
              assertThat(event.studentId()).isEqualTo(ana);
              assertThat(event.tutorId()).isEqualTo(tutor);
            });
  }

  @Test
  @DisplayName("A late cancellation by the student requires confirmation and has no refund")
  void aLateCancellationByTheStudentHasNoRefund() throws Exception {

    UUID bookingId = aBookingOfAna(null);
    clock.set(start.minus(LATE));

    cancel(ana, bookingId, false)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
        .andExpect(jsonPath("$.late").value(true))
        .andExpect(jsonPath("$.refundWillBeIssued").value(false))
        .andExpect(jsonPath("$.incidentWillBeRecorded").value(false))
        .andExpect(jsonPath("$.confirmationRequired").value(true));

    assertNothingChanged(bookingId);

    cancel(ana, bookingId, true).andExpect(status().isNoContent());

    assertThat(bookingRow(bookingId))
        .containsEntry("status", "CANCELLED")
        .containsEntry("cancelled_by", "STUDENT")
        .containsEntry("cancelled_late", true);
    assertThat(sessionStatus(bookingId)).isEqualTo("CANCELLED");
    assertThat(balanceOf(ana)).isEqualTo(4);
    assertThat(refundsOf(bookingId)).isZero();
    assertThat(incidentsOf(bookingId)).isZero();
    assertThat(cancelled.of(bookingId))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.cancelledBy()).isEqualTo(BookingCancelled.CancelledBy.STUDENT);
              assertThat(event.late()).isTrue();
            });
  }

  @Test
  @DisplayName("A cancelled booking is no longer reserved in the tutor's agenda")
  void aCancelledBookingIsNoLongerReserved() throws Exception {

    UUID bookingId = aBookingOfAna(null);
    assertThat(blockAt(start).getBookingId()).isEqualTo(bookingId);

    clock.set(start.minus(AHEAD));
    cancel(ana, bookingId, false).andExpect(status().isNoContent());

    HourBlock freed = blockAt(start);
    assertThat(freed.getBookingId()).isNull();
    assertThat(freed.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    // Somebody else can take it again.
    grant(bruno, 2);
    hold(bruno, start, 1).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("A session cannot be cancelled at or after its scheduled start")
  void aStartedSessionCannotBeCancelled() throws Exception {

    UUID bookingId = aBookingOfAna(null);
    clock.set(start);

    cancel(ana, bookingId, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("after it has started")));

    assertNothingChanged(bookingId);
    assertThat(refundsOf(bookingId)).isZero();
  }

  @Test
  @DisplayName("The tutor cancels ahead of time and the student is refunded")
  void theTutorCancelsAheadOfTime() throws Exception {

    UUID bookingId = aBookingOfAna(null);
    clock.set(start.minus(AHEAD));

    cancel(tutor, bookingId, false).andExpect(status().isNoContent());

    assertThat(bookingRow(bookingId))
        .containsEntry("status", "CANCELLED")
        .containsEntry("cancelled_by", "TUTOR")
        .containsEntry("cancelled_late", false);
    assertThat(sessionStatus(bookingId)).isEqualTo("CANCELLED");
    assertThat(balanceOf(ana)).isEqualTo(5);
    assertThat(refundsOf(bookingId)).isEqualTo(1);
    assertThat(blockAt(start).getStatus()).isEqualTo(HourBlockStatus.RELEASED);
    assertThat(incidentsOf(bookingId)).isZero();
    assertThat(cancelled.of(bookingId))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.cancelledBy()).isEqualTo(BookingCancelled.CancelledBy.TUTOR);
              assertThat(event.late()).isFalse();
            });
  }

  @Test
  @DisplayName("The tutor cancels late and the student is refunded")
  void theTutorCancelsLate() throws Exception {

    UUID bookingId = aBookingOfAna(null);
    clock.set(start.minus(LATE));

    cancel(tutor, bookingId, false)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.late").value(true))
        .andExpect(jsonPath("$.refundWillBeIssued").value(true))
        .andExpect(jsonPath("$.incidentWillBeRecorded").value(true))
        .andExpect(jsonPath("$.confirmationRequired").value(true));

    assertNothingChanged(bookingId);
    assertThat(incidentsOf(bookingId)).isZero();

    cancel(tutor, bookingId, true).andExpect(status().isNoContent());

    assertThat(bookingRow(bookingId))
        .containsEntry("status", "CANCELLED")
        .containsEntry("cancelled_by", "TUTOR")
        .containsEntry("cancelled_late", true);
    assertThat(sessionStatus(bookingId)).isEqualTo("CANCELLED");
    assertThat(balanceOf(ana)).isEqualTo(5);
    assertThat(refundsOf(bookingId)).isEqualTo(1);
    assertThat(blockAt(start).getStatus()).isEqualTo(HourBlockStatus.RELEASED);
    assertThat(incidentsOf(bookingId)).isEqualTo(1);
    assertThat(cancelled.of(bookingId))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.cancelledBy()).isEqualTo(BookingCancelled.CancelledBy.TUTOR);
              assertThat(event.late()).isTrue();
            });
  }

  @Test
  @DisplayName("Someone who is not part of the booking cannot cancel it")
  void aStrangerCannotCancel() throws Exception {

    UUID bookingId = aBookingOfAna(null);
    clock.set(start.minus(AHEAD));

    cancel(carla, bookingId, false)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.path").value("/api/v1/bookings/" + bookingId));

    assertNothingChanged(bookingId);
    assertThat(refundsOf(bookingId)).isZero();
    assertThat(cancelled.of(bookingId)).isEmpty();
  }

  @Test
  @DisplayName("Cancelling twice changes nothing and refunds once")
  void cancellingTwiceRefundsOnce() throws Exception {

    UUID bookingId = aBookingOfAna(null);
    clock.set(start.minus(AHEAD));

    cancel(ana, bookingId, false).andExpect(status().isNoContent());
    Map<String, Object> afterTheFirst = bookingRow(bookingId);
    cancel(ana, bookingId, false).andExpect(status().isNoContent());

    assertThat(bookingRow(bookingId)).isEqualTo(afterTheFirst);
    assertThat(balanceOf(ana)).isEqualTo(5);
    assertThat(refundsOf(bookingId)).isEqualTo(1);
    assertThat(cancelled.of(bookingId)).hasSize(1);
  }

  @Test
  @DisplayName("A booking that does not exist in this university is not found")
  void aBookingOfAnotherUniversityIsNotFound() throws Exception {

    UUID bookingId = aBookingOfAna(null);
    clock.set(start.minus(AHEAD));

    mockMvc
        .perform(
            delete("/api/v1/bookings/{id}", UUID.randomUUID())
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", ana))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            delete("/api/v1/bookings/{id}", bookingId)
                .header("X-Tenant-Id", "PUCP")
                .header("X-User-Id", ana))
        .andExpect(status().isNotFound());

    assertNothingChanged(bookingId);
  }

  /**
   * Ana books the tutor's hour of {@value #HOUR}:00 tomorrow with five credits: three that expire
   * (or never, without an expiry) and two earned. Returns the booking.
   */
  private UUID aBookingOfAna(Instant seedExpiry) throws Exception {
    clock.set(start.minus(Duration.ofDays(1)));
    if (seedExpiry == null) {
      grant(ana, 5);
    } else {
      grant(ana, 3, CreditType.SEED, seedExpiry);
      grant(ana, 2);
    }
    hold(ana, start, 1).andExpect(status().isCreated());
    String answer =
        book(ana, start, 1, "Normal forms")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID bookingId = UUID.fromString(JsonPath.read(answer, "$.id"));
    assertThat(sessionStatus(bookingId)).isEqualTo("SCHEDULED");
    return bookingId;
  }

  private ResultActions cancel(UUID who, UUID bookingId, boolean confirmLate) throws Exception {
    return mockMvc.perform(
        delete("/api/v1/bookings/{id}", bookingId)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", who)
            .param("confirmLate", String.valueOf(confirmLate)));
  }

  /** The booking, its session, the hour, the credits and the event are as they were booked. */
  private void assertNothingChanged(UUID bookingId) {
    assertThat(bookingRow(bookingId))
        .containsEntry("status", "CONFIRMED")
        .containsEntry("cancelled_by", null)
        .containsEntry("cancelled_late", null);
    assertThat(sessionStatus(bookingId)).isEqualTo("SCHEDULED");
    assertThat(balanceOf(ana)).isEqualTo(4);
    // Read as a row: once the start has arrived the repository's search no longer returns it.
    assertThat(
            jdbc.queryForMap(
                "select status, booking_id from booking.hour_blocks"
                    + " where tenant_id = ? and tutor_id = ? and starts_at = ?::timestamptz",
                UPC,
                tutor,
                start.toString()))
        .containsEntry("status", "BOOKED")
        .containsEntry("booking_id", bookingId);
    assertThat(cancelled.of(bookingId)).isEmpty();
  }

  private Map<String, Object> bookingRow(UUID bookingId) {
    return jdbc.queryForMap(
        "select status, cancelled_by, cancelled_late from booking.bookings"
            + " where tenant_id = ? and id = ?",
        UPC,
        bookingId);
  }

  private String sessionStatus(UUID bookingId) {
    return jdbc.queryForObject(
        "select status from sessions.sessions where tenant_id = ? and booking_id = ?",
        String.class,
        UPC,
        bookingId);
  }

  private int refundsOf(UUID bookingId) {
    return jdbc.queryForObject(
        """
        select count(*) from wallet.ledger_entries
        where tenant_id = ? and reference_type = 'BOOKING' and reference_id = ?
          and reason = 'BOOKING_REFUND'
        """,
        Integer.class,
        UPC,
        bookingId);
  }

  private int incidentsOf(UUID bookingId) {
    return jdbc.queryForObject(
        """
        select count(*) from booking.tutor_reliability
        where tenant_id = ? and tutor_id = ? and booking_id = ? and kind = 'LATE_CANCELLATION'
        """,
        Integer.class,
        UPC,
        tutor,
        bookingId);
  }

  private List<Map<String, Object>> lotsOf(UUID student) {
    return jdbc.queryForList(
        """
        select lot.credit_type, lot.remaining_amount, lot.expires_at
        from wallet.credit_lots lot
        join wallet.credit_accounts account on account.id = lot.account_id
        where account.tenant_id = ? and account.user_id = ?
        order by lot.credit_type
        """,
        UPC,
        student);
  }
}
