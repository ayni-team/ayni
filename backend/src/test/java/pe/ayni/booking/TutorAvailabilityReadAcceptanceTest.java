package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.ayni.config.SignedInPeople.Person;

/**
 * Reading back the availability a tutor declared, over HTTP against a real PostgreSQL, signed in
 * with real session tokens. One test per reading scenario of {@code
 * features/US19-tutor-availability.feature}, {@code US20-date-exceptions.feature} and {@code
 * US22-pause-availability.feature}, with the same name.
 *
 * <p>The tutor of {@link SignedInBookingScenario} already declared tomorrow's weekday from nine to
 * twelve, valid from today; every scenario starts from that range.
 */
class TutorAvailabilityReadAcceptanceTest extends SignedInBookingScenario {

  /** A weekly range as the test declared it, to know the order the answer must keep. */
  private record Declared(String id, DayOfWeek day, LocalTime startsAt) {}

  private String availability(Person person) throws Exception {
    return mockMvc
        .perform(as(person, get("/api/v1/tutor/availability")))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String declareRange(
      DayOfWeek day, String from, String to, LocalDate validFrom, LocalDate validUntil)
      throws Exception {
    String created =
        mockMvc
            .perform(
                as(tutor, post("/api/v1/tutor/availability"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"dayOfWeek":"%s","startsAtTime":"%s","endsAtTime":"%s",
                         "validFrom":"%s","validUntil":%s}
                        """
                            .formatted(
                                day,
                                from,
                                to,
                                validFrom,
                                validUntil == null ? "null" : "\"" + validUntil + "\"")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(created, "$.id");
  }

  private String declareException(LocalDate date, String from, String to, String kind)
      throws Exception {
    String times =
        from == null
            ? "\"startsAtTime\":null,\"endsAtTime\":null"
            : "\"startsAtTime\":\"%s\",\"endsAtTime\":\"%s\"".formatted(from, to);
    String created =
        mockMvc
            .perform(
                as(tutor, post("/api/v1/tutor/availability/exceptions"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"exceptionDate":"%s",%s,"kind":"%s"}
                        """
                            .formatted(date, times, kind)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(created, "$.id");
  }

  private String declarePause(LocalDate startsOn, LocalDate endsOn) throws Exception {
    String created =
        mockMvc
            .perform(
                as(tutor, post("/api/v1/tutor/availability/pauses"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"startsOn":"%s","endsOn":"%s"}
                        """
                            .formatted(startsOn, endsOn)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(created, "$.id");
  }

  /** The background range of tomorrow's weekday, read back as the first answer has it. */
  private String backgroundRange() throws Exception {
    List<String> ids = JsonPath.read(availability(tutor), "$.patterns[*].id");
    assertThat(ids).hasSize(1);
    return ids.getFirst();
  }

  @Test
  @DisplayName("A tutor reads back the weekly ranges they declared")
  void aTutorReadsBackTheWeeklyRangesTheyDeclared() throws Exception {
    DayOfWeek tomorrow = today().plusDays(1).getDayOfWeek();
    String nineToTwelve = backgroundRange();
    String afternoon = declareRange(tomorrow, "15:00:00", "17:00:00", today(), null);
    String untilTheTerm =
        declareRange(tomorrow.plus(3), "08:00:00", "10:00:00", today(), today().plusDays(60));
    String later = declareRange(tomorrow.plus(5), "18:00:00", "19:00:00", today().plusDays(14), null);
    String removed = declareRange(tomorrow.plus(2), "10:00:00", "11:00:00", today(), null);
    mockMvc
        .perform(as(tutor, delete("/api/v1/tutor/availability/" + removed)))
        .andExpect(status().isOk());
    // A range whose last day is already gone gives no more hours.
    declareRange(tomorrow.plus(4), "10:00:00", "11:00:00", today().minusDays(30), today().minusDays(1));

    String read = availability(tutor);

    assertThat(JsonPath.<String>read(read, "$.timezone")).isEqualTo("America/Lima");
    List<String> expectedOrder =
        List.of(
                new Declared(nineToTwelve, tomorrow, LocalTime.of(9, 0)),
                new Declared(afternoon, tomorrow, LocalTime.of(15, 0)),
                new Declared(untilTheTerm, tomorrow.plus(3), LocalTime.of(8, 0)),
                new Declared(later, tomorrow.plus(5), LocalTime.of(18, 0)))
            .stream()
            .sorted(Comparator.comparing(Declared::day).thenComparing(Declared::startsAt))
            .map(Declared::id)
            .toList();
    assertThat(JsonPath.<List<String>>read(read, "$.patterns[*].id"))
        .containsExactlyElementsOf(expectedOrder)
        .doesNotContain(removed);
    String term = "$.patterns[?(@.id == '" + untilTheTerm + "')]";
    assertThat(JsonPath.<List<String>>read(read, term + ".dayOfWeek"))
        .containsExactly(tomorrow.plus(3).name());
    assertThat(JsonPath.<List<String>>read(read, term + ".startsAtTime")).containsExactly("08:00:00");
    assertThat(JsonPath.<List<String>>read(read, term + ".endsAtTime")).containsExactly("10:00:00");
    assertThat(JsonPath.<List<String>>read(read, term + ".validFrom"))
        .containsExactly(today().toString());
    assertThat(JsonPath.<List<String>>read(read, term + ".validUntil"))
        .containsExactly(today().plusDays(60).toString());
    assertThat(JsonPath.<List<Object>>read(read, "$.patterns[?(@.id == '" + nineToTwelve + "')].validUntil"))
        .containsExactly((Object) null);
    assertThat(JsonPath.<List<Object>>read(read, "$.exceptions")).isEmpty();
    assertThat(JsonPath.<List<Object>>read(read, "$.pauses")).isEmpty();
  }

  @Test
  @DisplayName("A tutor reads back their date exceptions")
  void aTutorReadsBackTheirDateExceptions() throws Exception {
    declareException(today().minusDays(1), null, null, "REMOVE");
    String todayExtra = declareException(today(), "18:00:00", "19:00:00", "ADD");
    String tomorrowsTen = declareException(today().plusDays(1), "10:00:00", "11:00:00", "REMOVE");
    String laterAfternoon = declareException(today().plusDays(2), "14:00:00", "16:00:00", "ADD");
    String laterMorning = declareException(today().plusDays(2), "07:00:00", "08:00:00", "ADD");
    String wholeDay = declareException(today().plusDays(5), null, null, "REMOVE");

    String read = availability(tutor);

    assertThat(JsonPath.<List<String>>read(read, "$.exceptions[*].id"))
        .containsExactly(todayExtra, tomorrowsTen, laterMorning, laterAfternoon, wholeDay);
    String ten = "$.exceptions[?(@.id == '" + tomorrowsTen + "')]";
    assertThat(JsonPath.<List<String>>read(read, ten + ".exceptionDate"))
        .containsExactly(today().plusDays(1).toString());
    assertThat(JsonPath.<List<String>>read(read, ten + ".startsAtTime")).containsExactly("10:00:00");
    assertThat(JsonPath.<List<String>>read(read, ten + ".endsAtTime")).containsExactly("11:00:00");
    assertThat(JsonPath.<List<String>>read(read, ten + ".kind")).containsExactly("REMOVE");
    String day = "$.exceptions[?(@.id == '" + wholeDay + "')]";
    assertThat(JsonPath.<List<Object>>read(read, day + ".startsAtTime")).containsExactly((Object) null);
    assertThat(JsonPath.<List<Object>>read(read, day + ".endsAtTime")).containsExactly((Object) null);
    assertThat(JsonPath.<List<String>>read(read, "$.exceptions[?(@.id == '" + laterMorning + "')].kind"))
        .containsExactly("ADD");

    // Two days later, today's and tomorrow's exceptions are in the past and are no longer listed.
    advance(Duration.ofDays(2));
    assertThat(JsonPath.<List<String>>read(availability(tutor), "$.exceptions[*].id"))
        .containsExactly(laterMorning, laterAfternoon, wholeDay);
  }

  @Test
  @DisplayName("A tutor reads back their pause")
  void aTutorReadsBackTheirPause() throws Exception {
    declarePause(today().minusDays(10), today().minusDays(5));
    String later = declarePause(today().plusDays(20), today().plusDays(25));
    String current = declarePause(today(), today().plusDays(1));

    String read = availability(tutor);

    assertThat(JsonPath.<List<String>>read(read, "$.pauses[*].id")).containsExactly(current, later);
    String now = "$.pauses[?(@.id == '" + current + "')]";
    assertThat(JsonPath.<List<String>>read(read, now + ".startsOn")).containsExactly(today().toString());
    assertThat(JsonPath.<List<String>>read(read, now + ".endsOn"))
        .containsExactly(today().plusDays(1).toString());

    // Ended early, the pause no longer keeps the tutor away and is not listed; the later one is.
    mockMvc
        .perform(as(tutor, delete("/api/v1/tutor/availability/pauses/" + current)))
        .andExpect(status().isOk());
    assertThat(JsonPath.<List<String>>read(availability(tutor), "$.pauses[*].id"))
        .containsExactly(later);
  }

  @Test
  @DisplayName("A tutor reads back only their own availability")
  void aTutorReadsBackOnlyTheirOwnAvailability() throws Exception {
    declareException(today().plusDays(1), "10:00:00", "11:00:00", "REMOVE");
    declarePause(today().plusDays(20), today().plusDays(25));
    assertThat(JsonPath.<List<Object>>read(availability(tutor), "$.patterns")).hasSize(1);

    // Another tutor of the same university reads their own, which is nothing yet.
    String anas = availability(ana);
    assertThat(JsonPath.<String>read(anas, "$.timezone")).isEqualTo("America/Lima");
    assertThat(JsonPath.<List<Object>>read(anas, "$.patterns")).isEmpty();
    assertThat(JsonPath.<List<Object>>read(anas, "$.exceptions")).isEmpty();
    assertThat(JsonPath.<List<Object>>read(anas, "$.pauses")).isEmpty();

    // Rows of another university under the tutor's own identifier are not the tutor's to read
    // here, and a student of that university sees nothing of this one.
    String elsewhere = people.aUniversity("B");
    jdbc.update(
        """
        insert into booking.availability_pauses (id, tenant_id, tutor_id, starts_on, ends_on)
        values (gen_random_uuid(), ?, ?, ?, ?)
        """,
        elsewhere,
        tutor.id(),
        Date.valueOf(today().plusDays(40)),
        Date.valueOf(today().plusDays(41)));
    assertThat(JsonPath.<List<Object>>read(availability(tutor), "$.pauses")).hasSize(1);
    String theirs = availability(people.aStudent(elsewhere));
    assertThat(JsonPath.<List<Object>>read(theirs, "$.patterns")).isEmpty();
    assertThat(JsonPath.<List<Object>>read(theirs, "$.exceptions")).isEmpty();
    assertThat(JsonPath.<List<Object>>read(theirs, "$.pauses")).isEmpty();

    // Without a session nobody is answered.
    mockMvc.perform(get("/api/v1/tutor/availability")).andExpect(status().isUnauthorized());
  }
}
