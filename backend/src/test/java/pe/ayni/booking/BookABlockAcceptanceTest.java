package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import pe.ayni.booking.application.ReleaseExpiredHoldsUseCase;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US03 over HTTP against a real PostgreSQL, one test per scenario of {@code
 * features/US03-book-a-block.feature}, plus the refusals the hold endpoint owes an honest answer.
 */
class BookABlockAcceptanceTest extends BookingScenario {

  @Autowired private ReleaseExpiredHoldsUseCase releaseExpiredHolds;
  @Autowired private BookingApi bookingApi;

  @Test
  @DisplayName("Holding hours takes them out of circulation for five minutes")
  void holdingHoursTakesThemOutOfCirculation() throws Exception {

    Instant now = clock.instant();

    hold(ana, tomorrowAt(9), 2)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tutorId").value(tutor.toString()))
        .andExpect(jsonPath("$.startsAt").value(tomorrowAt(9).toString()))
        .andExpect(jsonPath("$.endsAt").value(tomorrowAt(11).toString()))
        .andExpect(jsonPath("$.hours").value(2))
        .andExpect(jsonPath("$.heldUntil").value(now.plus(Duration.ofMinutes(5)).toString()))
        .andExpect(jsonPath("$.blockIds", hasSize(2)));

    for (Instant start : new Instant[] {tomorrowAt(9), tomorrowAt(10)}) {
      HourBlock block = blockAt(start);
      assertThat(block.getStatus()).isEqualTo(HourBlockStatus.HELD);
      assertThat(block.getHeldBy()).isEqualTo(ana);
    }
    assertThat(blockAt(tomorrowAt(11)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("Block taken seconds earlier by another student: the hold is refused clearly")
  void anHourAnotherStudentHoldsCannotBeTaken() throws Exception {

    hold(ana, tomorrowAt(10), 1).andExpect(status().isCreated());

    hold(bruno, tomorrowAt(10), 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.error").value("Conflict"))
        .andExpect(jsonPath("$.message", containsString("Another student is holding this hour")))
        .andExpect(jsonPath("$.path").value("/api/v1/bookings/holds"))
        .andExpect(jsonPath("$.timestamp").exists());

    assertThat(blockAt(tomorrowAt(10)).getHeldBy()).isEqualTo(ana);
  }

  @Test
  @DisplayName("Temporary hold expiry: after five minutes without confirming, the block is freed")
  void anUnconfirmedHoldExpiresAfterFiveMinutes() throws Exception {

    hold(ana, tomorrowAt(11), 1).andExpect(status().isCreated());

    clock.advance(Duration.ofMinutes(4));
    hold(bruno, tomorrowAt(11), 1).andExpect(status().isConflict());

    clock.advance(Duration.ofMinutes(1).plusSeconds(1));
    TenantContext.runAs(UPC, releaseExpiredHolds::forCurrentUniversity);

    HourBlock freed = blockAt(tomorrowAt(11));
    assertThat(freed.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(freed.getHeldBy()).isNull();
    assertThat(freed.getHeldUntil()).isNull();

    hold(bruno, tomorrowAt(11), 1).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("Leaving the confirmation gives the hours back at once, and twice is harmless")
  void leavingTheConfirmationGivesTheHoursBack() throws Exception {

    hold(ana, tomorrowAt(9), 2).andExpect(status().isCreated());

    release(ana, tomorrowAt(9), 2).andExpect(status().isNoContent());
    release(ana, tomorrowAt(9), 2).andExpect(status().isNoContent());

    assertThat(blockAt(tomorrowAt(9)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(blockAt(tomorrowAt(10)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    hold(bruno, tomorrowAt(9), 2).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("Giving back only touches the student's own hold")
  void givingBackLeavesSomebodyElsesHoldAlone() throws Exception {

    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());

    release(bruno, tomorrowAt(9), 1).andExpect(status().isNoContent());

    assertThat(blockAt(tomorrowAt(9)).getHeldBy()).isEqualTo(ana);
  }

  @Test
  @DisplayName("Hours the tutor does not offer as one stretch are refused")
  void hoursTheTutorDoesNotOfferAreRefused() throws Exception {

    // The tutor is free until twelve, so the second hour from eleven does not exist.
    hold(ana, tomorrowAt(11), 2)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("2 consecutive hours")));

    assertThat(blockAt(tomorrowAt(11)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("An hour of the tutor cannot be held from another university")
  void anHourCannotBeHeldFromAnotherUniversity() throws Exception {

    hold("PUCP", ana, tomorrowAt(9), 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("does not offer")));

    assertThat(blockAt(tomorrowAt(9)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("A student whose account is not active cannot hold hours")
  void aStudentWhoIsNotActiveCannotHold() throws Exception {

    when(identity.isActive(ana)).thenReturn(false);

    hold(ana, tomorrowAt(9), 1)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message", containsString("not active")));
  }

  @Test
  @DisplayName("A tutor cannot hold their own hours")
  void aTutorCannotHoldTheirOwnHours() throws Exception {

    hold(tutor, tomorrowAt(9), 1)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("own hours")));
  }

  @Test
  @DisplayName("Successful booking: credits deducted, block taken for others, BookingConfirmed")
  void successfulBooking() throws Exception {

    grant(ana, 5);
    hold(ana, tomorrowAt(9), 2).andExpect(status().isCreated());

    MvcResult answer =
        book(ana, tomorrowAt(9), 2, "Normal forms before Friday's exam")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.studentId").value(ana.toString()))
            .andExpect(jsonPath("$.tutorId").value(tutor.toString()))
            .andExpect(jsonPath("$.catalogItemId").value(subject.toString()))
            .andExpect(jsonPath("$.startsAt").value(tomorrowAt(9).toString()))
            .andExpect(jsonPath("$.endsAt").value(tomorrowAt(11).toString()))
            .andExpect(jsonPath("$.hours").value(2))
            .andExpect(jsonPath("$.creditsCharged").value(2))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.blockIds", hasSize(2)))
            .andReturn();
    UUID bookingId = idOf(answer);

    assertThat(balanceOf(ana)).isEqualTo(3);
    for (Instant start : new Instant[] {tomorrowAt(9), tomorrowAt(10)}) {
      HourBlock block = blockAt(start);
      assertThat(block.getStatus()).isEqualTo(HourBlockStatus.BOOKED);
      assertThat(block.getBookingId()).isEqualTo(bookingId);
    }
    hold(bruno, tomorrowAt(10), 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("already booked")));

    List<BookingConfirmed> confirmed =
        events.stream(BookingConfirmed.class)
            .filter(event -> event.bookingId().equals(bookingId))
            .toList();
    assertThat(confirmed).hasSize(1);
    assertThat(confirmed.getFirst().tenantId()).isEqualTo(UPC);
    assertThat(confirmed.getFirst().creditsCharged()).isEqualTo(Credits.of(2));
    assertThat(confirmed.getFirst().blockIds())
        .containsExactly(blockAt(tomorrowAt(9)).getId(), blockAt(tomorrowAt(10)).getId());

    // Sessions heard it and created the session, with a room nobody can guess.
    Map<String, Object> session =
        jdbc.queryForMap(
            """
            select status, room_name, student_id, tutor_id, scheduled_start, scheduled_end
            from sessions.sessions where tenant_id = ? and booking_id = ?
            """,
            UPC,
            bookingId);
    assertThat(session.get("status")).isEqualTo("SCHEDULED");
    assertThat(session.get("student_id")).isEqualTo(ana);
    assertThat(session.get("tutor_id")).isEqualTo(tutor);
    assertThat((String) session.get("room_name")).matches("ayni-[0-9a-f]{32}");
    assertThat(((Timestamp) session.get("scheduled_start")).toInstant()).isEqualTo(tomorrowAt(9));
    assertThat(((Timestamp) session.get("scheduled_end")).toInstant()).isEqualTo(tomorrowAt(11));
  }

  @Test
  @DisplayName("Booking the same hours a second time is refused and charges nothing")
  void aSecondAttemptIsRefused() throws Exception {

    grant(ana, 5);
    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());
    book(ana, tomorrowAt(9), 1, "Joins").andExpect(status().isCreated());

    book(ana, tomorrowAt(9), 1, "Joins")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("already booked")));

    assertThat(balanceOf(ana)).isEqualTo(4);
    assertThat(bookingsOf(ana)).isEqualTo(1);
  }

  @Test
  @DisplayName("Consumption by expiry order: the credits closest to expiring are spent first")
  void creditsClosestToExpiringAreSpentFirst() throws Exception {

    Instant now = clock.instant();
    grant(ana, 5, CreditType.ALLOCATED, now.plus(Duration.ofDays(60)));
    grant(ana, 3, CreditType.EARNED, null);
    grant(ana, 2, CreditType.SEED, now.plus(Duration.ofDays(10)));

    hold(ana, tomorrowAt(9), 3).andExpect(status().isCreated());
    UUID bookingId =
        idOf(book(ana, tomorrowAt(9), 3, "Indexes").andExpect(status().isCreated()).andReturn());

    // What wallet recorded for this booking: every SEED credit, then one ALLOCATED, no EARNED.
    mockMvc
        .perform(
            get("/api/v1/wallet/movements")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", ana)
                .param("reason", "BOOKING_CHARGE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[?(@.creditType=='SEED')].amount", contains(2)))
        .andExpect(jsonPath("$.items[?(@.creditType=='ALLOCATED')].amount", contains(1)))
        .andExpect(jsonPath("$.items[?(@.creditType=='EARNED')]", hasSize(0)))
        .andExpect(jsonPath("$.items[*].referenceId", everyItem(is(bookingId.toString()))));

    mockMvc
        .perform(get("/api/v1/wallet").header("X-Tenant-Id", UPC).header("X-User-Id", ana))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(7))
        .andExpect(jsonPath("$.byType[?(@.type=='ALLOCATED')].available", contains(4)))
        .andExpect(jsonPath("$.byType[?(@.type=='EARNED')].available", contains(3)));
  }

  @Test
  @DisplayName("Insufficient credits: the answer says how many are missing and nothing moves")
  void insufficientCreditsSayHowManyAreMissing() throws Exception {

    grant(ana, 1);
    hold(ana, tomorrowAt(9), 3).andExpect(status().isCreated());

    book(ana, tomorrowAt(9), 3, "Transactions")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message").value("Not enough credits: 2 more are needed to book these hours"))
        .andExpect(jsonPath("$.path").value("/api/v1/bookings"));

    assertThat(balanceOf(ana)).isEqualTo(1);
    assertThat(chargesOf(ana)).isZero();
    assertThat(bookingsOf(ana)).isZero();
    for (int hour = 9; hour < 12; hour++) {
      assertThat(blockAt(tomorrowAt(hour)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    }
  }

  @Test
  @DisplayName("Block taken seconds earlier by another student: confirming it charges nothing")
  void confirmingAnHourAnotherStudentHoldsChargesNothing() throws Exception {

    grant(bruno, 3);
    hold(ana, tomorrowAt(10), 1).andExpect(status().isCreated());

    book(bruno, tomorrowAt(10), 1, "Views")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("Another student is holding this hour")));

    assertThat(balanceOf(bruno)).isEqualTo(3);
    assertThat(chargesOf(bruno)).isZero();
    // Bruno's failed attempt gives back only what Bruno held, which is nothing.
    assertThat(blockAt(tomorrowAt(10)).getHeldBy()).isEqualTo(ana);
  }

  @Test
  @DisplayName("Temporary hold expiry: confirming after five minutes is refused and charges nothing")
  void confirmingAnExpiredHoldIsRefused() throws Exception {

    grant(ana, 3);
    hold(ana, tomorrowAt(11), 1).andExpect(status().isCreated());
    clock.advance(Duration.ofMinutes(5).plusSeconds(1));

    book(ana, tomorrowAt(11), 1, "Stored procedures")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("no longer holding")));

    assertThat(balanceOf(ana)).isEqualTo(3);
    assertThat(blockAt(tomorrowAt(11)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("Failure during the booking: the balance is as it was and the block is freed")
  void aFailureDuringTheBookingLeavesNothingBehind() throws Exception {

    grant(ana, 3);
    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());
    failingConfirmation.failNextConfirmationOf(ana);

    book(ana, tomorrowAt(9), 1, "Triggers")
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "The booking could not be completed. Nothing was charged and the hours you "
                        + "held are free again"));

    assertThat(balanceOf(ana)).isEqualTo(3);
    assertThat(chargesOf(ana)).isZero();
    assertThat(bookingsOf(ana)).isZero();
    // The booking was never announced, so no session was created for it.
    assertThat(sessionsOf(ana)).isZero();
    HourBlock block = blockAt(tomorrowAt(9));
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(block.getHeldBy()).isNull();
    assertThat(block.getBookingId()).isNull();
  }

  @Test
  @DisplayName("Need description: it travels with the booking and is readable by other modules")
  void theNeedDescriptionTravelsWithTheBooking() throws Exception {

    grant(ana, 3);
    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());

    UUID bookingId =
        idOf(
            book(ana, tomorrowAt(9), 1, "Normal forms before Friday's exam")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.needDescription").value("Normal forms before Friday's exam"))
                .andReturn());

    AtomicReference<BookingView> seenByOthers = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> seenByOthers.set(bookingApi.requireBooking(bookingId)));
    assertThat(seenByOthers.get().needDescription()).isEqualTo("Normal forms before Friday's exam");
    assertThat(seenByOthers.get().status()).isEqualTo(BookingStatus.CONFIRMED);

    // The session keeps the booking's id, and through it reaches the description.
    UUID sessionsBooking =
        jdbc.queryForObject(
            "select booking_id from sessions.sessions where tenant_id = ? and student_id = ?",
            UUID.class,
            UPC,
            ana);
    AtomicReference<BookingView> seenFromTheSession = new AtomicReference<>();
    TenantContext.runAs(
        UPC, () -> seenFromTheSession.set(bookingApi.requireBooking(sessionsBooking)));
    assertThat(seenFromTheSession.get().needDescription())
        .isEqualTo("Normal forms before Friday's exam");
  }

  @Test
  @DisplayName("A booking without a need description is refused")
  void aBookingWithoutANeedDescriptionIsRefused() throws Exception {

    grant(ana, 3);
    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());

    book(ana, tomorrowAt(9), 1, "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("needDescription must not be blank"));

    assertThat(balanceOf(ana)).isEqualTo(3);
  }

  @Test
  @DisplayName("Hours cannot be booked without holding them first")
  void hoursCannotBeBookedWithoutAHold() throws Exception {

    grant(ana, 3);

    book(ana, tomorrowAt(9), 1, "Anything")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("no longer holding")));

    assertThat(balanceOf(ana)).isEqualTo(3);
  }

  @Test
  @DisplayName("A tutor no longer enabled for the subject cannot be booked, and the hold is freed")
  void aTutorWhoIsNotEnabledCannotBeBooked() throws Exception {

    grant(ana, 3);
    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());
    when(skills.isTutorEnabledFor(tutor, subject)).thenReturn(false);

    book(ana, tomorrowAt(9), 1, "Anything")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("not enabled")));

    assertThat(balanceOf(ana)).isEqualTo(3);
    assertThat(blockAt(tomorrowAt(9)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }

  @Test
  @DisplayName("A subject the university cannot see is not found")
  void aSubjectOfAnotherUniversityIsNotFound() throws Exception {

    grant(ana, 3);
    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());
    when(skills.requireItem(subject))
        .thenThrow(new NoSuchElementException("catalog item %s not found".formatted(subject)));

    book(ana, tomorrowAt(9), 1, "Anything")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", containsString("not found")));

    assertThat(balanceOf(ana)).isEqualTo(3);
  }

  private static UUID idOf(MvcResult answer) throws Exception {
    return UUID.fromString(JsonPath.read(answer.getResponse().getContentAsString(), "$.id"));
  }

  @Test
  @DisplayName("A hold request without hours is refused in the common error shape")
  void aHoldRequestWithoutHoursIsRefused() throws Exception {

    mockMvc
        .perform(
            post("/api/v1/bookings/holds")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tutorId\":\"%s\",\"start\":\"%s\"}".formatted(tutor, tomorrowAt(9))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("hours must not be null"))
        .andExpect(jsonPath("$.path").value("/api/v1/bookings/holds"));
  }
}
