package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.ayni.booking.domain.model.HourBlockStatus;
import pe.ayni.shared.events.HoursGenerated;
import pe.ayni.shared.events.HoursWithdrawn;

/**
 * US20 and US22 over HTTP against a real PostgreSQL: a tutor who pauses or changes one date sees
 * the hours that already existed follow at once, one test per scenario of {@code
 * features/US20-date-exceptions.feature} and {@code features/US22-pause-availability.feature}.
 *
 * <p>The tutor of {@link BookingScenario} is free tomorrow from nine to twelve, Lima time, with the
 * hours already generated, which is exactly the case a pause usually meets: it arrives after the
 * hours it covers exist. Matching is the real one, so the search is checked too.
 */
class AdjustAvailabilityAcceptanceTest extends BookingScenario {

  private LocalDate tomorrow() {
    return today().plusDays(1);
  }

  private ResultActions pause(LocalDate from, LocalDate to) throws Exception {
    return mockMvc.perform(
        post("/api/v1/tutor/availability/pauses")
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", tutor)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"startsOn\":\"%s\",\"endsOn\":\"%s\"}".formatted(from, to)));
  }

  private ResultActions pauseStatus() throws Exception {
    return mockMvc.perform(
        get("/api/v1/tutor/availability/pauses/current")
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", tutor));
  }

  private ResultActions reactivate(UUID pauseId) throws Exception {
    return mockMvc.perform(
        delete("/api/v1/tutor/availability/pauses/{id}", pauseId)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", tutor));
  }

  private ResultActions removePattern(UUID patternId) throws Exception {
    return mockMvc.perform(
        delete("/api/v1/tutor/availability/{id}", patternId)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", tutor));
  }

  private UUID pauseId(LocalDate from, LocalDate to) {
    return jdbc.queryForObject(
        """
        select id from booking.availability_pauses
        where tenant_id = ? and tutor_id = ? and starts_on = ? and ends_on = ?
        """,
        UUID.class,
        UPC,
        tutor,
        from,
        to);
  }

  private UUID weeklyPatternId() {
    return jdbc.queryForObject(
        "select id from booking.availability_patterns where tenant_id = ? and tutor_id = ?",
        UUID.class,
        UPC,
        tutor);
  }

  private ResultActions exception(String kind, LocalDate date, String from, String to)
      throws Exception {
    String times =
        from == null ? "" : ",\"startsAtTime\":\"%s\",\"endsAtTime\":\"%s\"".formatted(from, to);
    return mockMvc.perform(
        post("/api/v1/tutor/availability/exceptions")
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", tutor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"exceptionDate\":\"%s\",\"kind\":\"%s\"%s}".formatted(date, kind, times)));
  }

  private HourBlockStatus statusAt(int hour) {
    return blockAt(tomorrowAt(hour)).getStatus();
  }

  /** Offers the search holds for the tutor's hour, read as rows: matching is another module. */
  private int offersAt(int hour) {
    return jdbc.queryForObject(
        "select count(*) from matching.available_offers where tenant_id = ? and block_id = ?",
        Integer.class,
        UPC,
        blockAt(tomorrowAt(hour)).getId());
  }

  private List<UUID> withdrawnByEvents() {
    return events.stream(HoursWithdrawn.class)
        .filter(event -> event.tutorId().equals(tutor))
        .flatMap(event -> event.blockIds().stream())
        .toList();
  }

  @Test
  @DisplayName("Pausing withdraws the hours that already existed")
  void pausingWithdrawsTheHoursThatExisted() throws Exception {

    assertThat(offersAt(9)).as("the hour is in the search before the pause").isEqualTo(1);

    pause(tomorrow(), tomorrow())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.withdrawnHours").value(3))
        .andExpect(jsonPath("$.bookedHoursKept").value(0))
        .andExpect(jsonPath("$.notice").value(nullValue()));

    for (int hour = 9; hour < 12; hour++) {
      assertThat(statusAt(hour)).isEqualTo(HourBlockStatus.WITHDRAWN);
      assertThat(offersAt(hour)).as("out of the search").isZero();
    }
    assertThat(withdrawnByEvents())
        .containsExactlyInAnyOrder(
            blockAt(tomorrowAt(9)).getId(),
            blockAt(tomorrowAt(10)).getId(),
            blockAt(tomorrowAt(11)).getId());
    hold(ana, tomorrowAt(9), 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("no longer offered")));
  }

  @Test
  @DisplayName("A booked hour stands through a pause")
  void aBookedHourStandsThroughAPause() throws Exception {

    grant(ana, 3);
    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());
    book(ana, tomorrowAt(9), 1, "Normal forms").andExpect(status().isCreated());

    pause(tomorrow(), tomorrow())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.withdrawnHours").value(2))
        .andExpect(jsonPath("$.bookedHoursKept").value(1))
        .andExpect(
            jsonPath("$.notice")
                .value(
                    "1 booked hour in this period stands: changing availability does not cancel"
                        + " bookings."));

    assertThat(statusAt(9)).isEqualTo(HourBlockStatus.BOOKED);
    assertThat(bookingsOf(ana)).isEqualTo(1);
    assertThat(balanceOf(ana)).isEqualTo(2);
  }

  @Test
  @DisplayName("An hour a student is holding is withdrawn, and confirming it is refused")
  void aHeldHourIsWithdrawn() throws Exception {

    grant(ana, 3);
    hold(ana, tomorrowAt(10), 1).andExpect(status().isCreated());

    pause(tomorrow(), tomorrow())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.withdrawnHours").value(3));

    assertThat(statusAt(10)).isEqualTo(HourBlockStatus.WITHDRAWN);
    book(ana, tomorrowAt(10), 1, "Joins")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("no longer offered")));
    assertThat(balanceOf(ana)).isEqualTo(3);
  }

  @Test
  @DisplayName("A pause beyond the generated weeks changes no hours now")
  void aPauseBeyondTheHorizonChangesNothingNow() throws Exception {

    pause(today().plusWeeks(8), today().plusWeeks(9))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.withdrawnHours").value(0));

    for (int hour = 9; hour < 12; hour++) {
      assertThat(statusAt(hour)).isEqualTo(HourBlockStatus.AVAILABLE);
    }
  }

  @Test
  @DisplayName("A tutor can see whether availability is paused and when the pause ends")
  void currentPauseStateIsVisible() throws Exception {
    pause(today(), tomorrow()).andExpect(status().isCreated());

    pauseStatus()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paused").value(true))
        .andExpect(jsonPath("$.pauseId").value(pauseId(today(), tomorrow()).toString()))
        .andExpect(jsonPath("$.startsOn").value(today().toString()))
        .andExpect(jsonPath("$.endsOn").value(tomorrow().toString()));
  }

  @Test
  @DisplayName("A tutor can reactivate early and restore the hours in the weekly pattern")
  void reactivatingEarlyRestoresWeeklyHours() throws Exception {
    pause(today(), tomorrow()).andExpect(status().isCreated());
    UUID pauseId = pauseId(today(), tomorrow());

    reactivate(pauseId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pauseId").value(pauseId.toString()))
        .andExpect(jsonPath("$.restoredHours").value(3));

    pauseStatus().andExpect(status().isOk()).andExpect(jsonPath("$.paused").value(false));
    for (int hour = 9; hour < 12; hour++) {
      assertThat(statusAt(hour)).isEqualTo(HourBlockStatus.AVAILABLE);
      assertThat(offersAt(hour)).as("restored to search").isEqualTo(1);
    }
    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("Removing a weekly range keeps confirmed bookings and withdraws its other hours")
  void removingWeeklyAvailabilityKeepsConfirmedBookings() throws Exception {
    grant(ana, 3);
    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());
    book(ana, tomorrowAt(9), 1, "Normal forms").andExpect(status().isCreated());

    removePattern(weeklyPatternId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.withdrawnHours").value(11))
        .andExpect(jsonPath("$.bookedHoursKept").value(1))
        .andExpect(jsonPath("$.notice").isNotEmpty());

    assertThat(statusAt(9)).isEqualTo(HourBlockStatus.BOOKED);
    assertThat(statusAt(10)).isEqualTo(HourBlockStatus.WITHDRAWN);
    assertThat(statusAt(11)).isEqualTo(HourBlockStatus.WITHDRAWN);
    assertThat(offersAt(10)).isZero();
    assertThat(bookingsOf(ana)).isEqualTo(1);
  }

  @Test
  @DisplayName("Removing a window withdraws only the hours it covers")
  void removingAWindowWithdrawsOnlyThoseHours() throws Exception {

    exception("REMOVE", tomorrow(), "10:00:00", "11:00:00")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.withdrawnHours").value(1))
        .andExpect(jsonPath("$.generatedHours").value(0));

    assertThat(statusAt(9)).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(statusAt(10)).isEqualTo(HourBlockStatus.WITHDRAWN);
    assertThat(statusAt(11)).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(offersAt(10)).isZero();
    assertThat(offersAt(9)).isEqualTo(1);

    // The same removal again finds nothing left to withdraw.
    exception("REMOVE", tomorrow(), "10:00:00", "11:00:00")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.withdrawnHours").value(0));
  }

  @Test
  @DisplayName("Removing a whole day withdraws all of its hours")
  void removingAWholeDayWithdrawsAllOfIt() throws Exception {

    exception("REMOVE", tomorrow(), null, null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.withdrawnHours").value(3));

    for (int hour = 9; hour < 12; hour++) {
      assertThat(statusAt(hour)).isEqualTo(HourBlockStatus.WITHDRAWN);
    }
  }

  @Test
  @DisplayName("Adding hours to a date makes them bookable at once")
  void addingHoursMakesThemBookableAtOnce() throws Exception {

    exception("ADD", tomorrow(), "14:00:00", "16:00:00")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.generatedHours").value(2))
        .andExpect(jsonPath("$.withdrawnHours").value(0))
        .andExpect(jsonPath("$.notice").value(nullValue()));

    assertThat(statusAt(14)).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(statusAt(15)).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(offersAt(14)).as("in the search at once").isEqualTo(1);
    assertThat(
            events.stream(HoursGenerated.class)
                .filter(event -> event.tutorId().equals(tutor))
                .flatMap(event -> event.blocks().stream())
                .map(HoursGenerated.Block::startsAt))
        .contains(tomorrowAt(14), tomorrowAt(15));
    hold(ana, tomorrowAt(14), 2).andExpect(status().isCreated());
  }
}
