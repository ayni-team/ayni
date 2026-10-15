package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * US21 over HTTP against a real PostgreSQL, signed in with real session tokens. One test per
 * scenario of {@code features/US21-tutor-agenda.feature}, with the same name.
 */
class TutorAgendaAcceptanceTest extends SignedInBookingScenario {

  private String agenda(String query) throws Exception {
    return mockMvc
        .perform(as(tutor, get("/api/v1/tutor/agenda" + query)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String history(String query) throws Exception {
    return mockMvc
        .perform(as(tutor, get("/api/v1/tutor/agenda/history" + query)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @DisplayName("Sorted agenda")
  void sortedAgenda() throws Exception {
    UUID brunos = book(bruno, tomorrowAt(11), 1, "Joins before the lab");
    UUID anas = book(ana, tomorrowAt(9), 2, "Normal forms before Friday");

    String week = agenda("");

    assertThat(JsonPath.<String>read(week, "$.from")).isEqualTo(today().toString());
    assertThat(JsonPath.<String>read(week, "$.to")).isEqualTo(today().plusDays(6).toString());
    assertThat(JsonPath.<String>read(week, "$.timezone")).isEqualTo("America/Lima");
    List<String> booked = JsonPath.read(week, "$.lines[?(@.kind == 'BOOKED')].booking.bookingId");
    assertThat(booked).containsExactly(anas.toString(), brunos.toString());
    String first = "$.lines[?(@.booking.bookingId == '" + anas + "')]";
    assertThat(JsonPath.<List<String>>read(week, first + ".booking.student.id")).containsExactly(ana.id().toString());
    assertThat(JsonPath.<List<String>>read(week, first + ".booking.student.name")).allMatch(name -> name.startsWith("Student "));
    assertThat(JsonPath.<List<String>>read(week, first + ".booking.catalogItem.name")).containsExactly("Databases I");
    assertThat(JsonPath.<List<String>>read(week, first + ".booking.needDescription")).containsExactly("Normal forms before Friday");
    assertThat(JsonPath.<List<Integer>>read(week, first + ".booking.hours")).containsExactly(2);
    assertThat(JsonPath.<List<String>>read(week, first + ".startsAt")).containsExactly(tomorrowAt(9).toString());
    assertThat(JsonPath.<List<String>>read(week, first + ".endsAt")).containsExactly(tomorrowAt(11).toString());
    assertThat(JsonPath.<List<String>>read(week, first + ".date")).containsExactly(today().plusDays(1).toString());
    assertThat(JsonPath.<List<String>>read(week, first + ".startTime")).containsExactly("09:00:00");
    assertThat(JsonPath.<List<String>>read(week, first + ".endTime")).containsExactly("11:00:00");
    assertThat(JsonPath.<List<Boolean>>read(week, first + ".booking.joinable")).containsExactly(false);
  }

  @Test
  @DisplayName("A tutoring about to start")
  void aTutoringAboutToStart() throws Exception {
    Instant start = tomorrowAt(9);
    UUID nine = book(ana, start, 1, "Normal forms before Friday");
    UUID eleven = book(bruno, tomorrowAt(11), 1, "Joins before the lab");
    LocalDate thatDay = today().plusDays(1);

    clock.set(start.minus(Duration.ofMinutes(10)));

    String day = agenda("?from=" + thatDay + "&to=" + thatDay);
    assertThat(JsonPath.<List<Boolean>>read(day, "$.lines[?(@.booking.bookingId == '" + nine + "')].booking.joinable"))
        .containsExactly(true);
    assertThat(JsonPath.<List<Boolean>>read(day, "$.lines[?(@.booking.bookingId == '" + eleven + "')].booking.joinable"))
        .containsExactly(false);
  }

  @Test
  @DisplayName("Telling what is booked from what is free")
  void tellingWhatIsBookedFromWhatIsFree() throws Exception {
    UUID nine = book(ana, tomorrowAt(9), 1, "Normal forms before Friday");
    // Bruno holds ten without booking it yet: still nobody's, so still free.
    mockMvc
        .perform(
            as(bruno, post("/api/v1/bookings/holds"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tutorId":"%s","start":"%s","hours":1}
                    """
                        .formatted(tutor.id(), tomorrowAt(10))))
        .andExpect(status().isCreated());
    // The tutor takes eleven out of the week: withdrawn, it is neither booked nor free.
    LocalDate thatDay = today().plusDays(1);
    mockMvc
        .perform(
            as(tutor, post("/api/v1/tutor/availability/exceptions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"exceptionDate":"%s","startsAtTime":"11:00:00","endsAtTime":"12:00:00",
                     "kind":"REMOVE"}
                    """
                        .formatted(thatDay)))
        .andExpect(status().is2xxSuccessful());

    String day = agenda("?from=" + thatDay + "&to=" + thatDay);

    assertThat(JsonPath.<List<String>>read(day, "$.lines[*].kind")).containsExactly("BOOKED", "FREE");
    assertThat(JsonPath.<List<String>>read(day, "$.lines[*].startTime")).containsExactly("09:00:00", "10:00:00");
    assertThat(JsonPath.<String>read(day, "$.lines[0].booking.bookingId")).isEqualTo(nine.toString());
    assertThat(JsonPath.<Object>read(day, "$.lines[1].booking")).isNull();
  }

  @Test
  @DisplayName("Consulting the tutorings already taught")
  void consultingTheTutoringsAlreadyTaught() throws Exception {
    UUID taught = book(ana, tomorrowAt(9), 1, "Normal forms before Friday");
    UUID cancelled = book(bruno, tomorrowAt(10), 1, "Joins before the lab");
    mockMvc
        .perform(as(bruno, delete("/api/v1/bookings/" + cancelled)))
        .andExpect(status().isNoContent());
    UUID ahead = book(bruno, tomorrowAt(11), 1, "Indexes");
    LocalDate thatDay = today().plusDays(1);

    clock.set(tomorrowAt(10).plus(Duration.ofMinutes(30)));

    String history = history("");
    assertThat(JsonPath.<String>read(history, "$.view")).isEqualTo("HISTORY");
    assertThat(JsonPath.<List<String>>read(history, "$.items[*].bookingId"))
        .containsExactly(cancelled.toString(), taught.toString());
    assertThat(JsonPath.<List<String>>read(history, "$.items[*].status")).containsExactly("CANCELLED", "CONFIRMED");
    assertThat(JsonPath.<List<String>>read(history, "$.items[*].needDescription"))
        .containsExactly("Joins before the lab", "Normal forms before Friday");
    assertThat(JsonPath.<String>read(history("?page=1&size=1"), "$.items[0].bookingId"))
        .isEqualTo(taught.toString());

    String day = agenda("?from=" + thatDay + "&to=" + thatDay);
    assertThat(JsonPath.<List<String>>read(day, "$.lines[?(@.kind == 'BOOKED')].booking.bookingId"))
        .containsExactly(ahead.toString());
  }

  @Test
  @DisplayName("The agenda is read in the university's time zone")
  void theAgendaIsReadInTheUniversitysTimeZone() throws Exception {
    // Tomorrow at 22:00 in Lima is already the day after in UTC.
    LocalDate thatDay = today().plusDays(1);
    mockMvc
        .perform(
            as(tutor, post("/api/v1/tutor/availability/exceptions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"exceptionDate":"%s","startsAtTime":"22:00:00","endsAtTime":"23:00:00",
                     "kind":"ADD"}
                    """
                        .formatted(thatDay)))
        .andExpect(status().is2xxSuccessful());

    String day = agenda("?from=" + thatDay + "&to=" + thatDay);
    assertThat(JsonPath.<List<String>>read(day, "$.lines[*].startTime"))
        .containsExactly("09:00:00", "10:00:00", "11:00:00", "22:00:00");
    assertThat(JsonPath.<String>read(day, "$.lines[3].date")).isEqualTo(thatDay.toString());
    assertThat(JsonPath.<String>read(day, "$.lines[3].startsAt")).isEqualTo(tomorrowAt(22).toString());
    assertThat(tomorrowAt(22).toString()).startsWith(thatDay.plusDays(1).toString());
    // Nothing of that evening leaks into the day after in Lima.
    String next = agenda("?from=" + thatDay.plusDays(1) + "&to=" + thatDay.plusDays(1));
    assertThat(JsonPath.<List<String>>read(next, "$.lines[*].startsAt")).doesNotContain(tomorrowAt(22).toString());

    mockMvc
        .perform(as(tutor, get("/api/v1/tutor/agenda?from=" + thatDay + "&to=" + thatDay.minusDays(1))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(as(tutor, get("/api/v1/tutor/agenda?from=" + thatDay + "&to=" + thatDay.plusDays(31))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(as(tutor, get("/api/v1/tutor/agenda?from=tomorrow")))
        .andExpect(status().isBadRequest());
  }
}
