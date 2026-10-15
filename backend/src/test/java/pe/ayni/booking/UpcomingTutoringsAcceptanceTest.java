package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.config.SignedInPeople.Person;

/**
 * US04 over HTTP against a real PostgreSQL, signed in with real session tokens. One test per
 * scenario of {@code features/US04-upcoming-tutorings.feature}, with the same name.
 */
class UpcomingTutoringsAcceptanceTest extends SignedInBookingScenario {

  private String mine(Person student, String query) throws Exception {
    return mockMvc
        .perform(as(student, get("/api/v1/bookings/mine" + query)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @DisplayName("Sorted list")
  void sortedList() throws Exception {
    UUID eleven = book(ana, tomorrowAt(11), 1, "Joins before the lab");
    UUID nine = book(ana, tomorrowAt(9), 2, "Normal forms before Friday");

    String upcoming = mine(ana, "");

    assertThat(JsonPath.<String>read(upcoming, "$.view")).isEqualTo("UPCOMING");
    assertThat(JsonPath.<String>read(upcoming, "$.timezone")).isEqualTo("America/Lima");
    assertThat(JsonPath.<Integer>read(upcoming, "$.total")).isEqualTo(2);
    assertThat(JsonPath.<List<String>>read(upcoming, "$.items[*].bookingId"))
        .containsExactly(nine.toString(), eleven.toString());
    assertThat(JsonPath.<String>read(upcoming, "$.items[0].startsAt"))
        .isEqualTo(tomorrowAt(9).toString());
    assertThat(JsonPath.<String>read(upcoming, "$.items[0].endsAt"))
        .isEqualTo(tomorrowAt(11).toString());
    assertThat(JsonPath.<Integer>read(upcoming, "$.items[0].hours")).isEqualTo(2);
    assertThat(JsonPath.<String>read(upcoming, "$.items[0].status")).isEqualTo("CONFIRMED");
    assertThat(JsonPath.<String>read(upcoming, "$.items[0].tutor.id")).isEqualTo(tutor.id().toString());
    assertThat(JsonPath.<String>read(upcoming, "$.items[0].tutor.name")).startsWith("Student ");
    assertThat(JsonPath.<String>read(upcoming, "$.items[0].catalogItem.id")).isEqualTo(subject.toString());
    assertThat(JsonPath.<String>read(upcoming, "$.items[0].catalogItem.name")).isEqualTo("Databases I");
    assertThat(JsonPath.<Boolean>read(upcoming, "$.items[0].joinable")).isFalse();

    // One page at a time.
    String first = mine(ana, "?page=0&size=1");
    String second = mine(ana, "?page=1&size=1");
    assertThat(JsonPath.<List<String>>read(first, "$.items[*].bookingId")).containsExactly(nine.toString());
    assertThat(JsonPath.<List<String>>read(second, "$.items[*].bookingId")).containsExactly(eleven.toString());
    assertThat(JsonPath.<Integer>read(second, "$.total")).isEqualTo(2);
    mockMvc.perform(as(ana, get("/api/v1/bookings/mine?size=101"))).andExpect(status().isBadRequest());
    mockMvc.perform(as(ana, get("/api/v1/bookings/mine?view=SOON"))).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("A tutoring about to start")
  void aTutoringAboutToStart() throws Exception {
    Instant start = tomorrowAt(9);
    UUID nine = book(ana, start, 1, "Normal forms before Friday");
    UUID eleven = book(ana, tomorrowAt(11), 1, "Joins before the lab");

    clock.set(start.minus(Duration.ofMinutes(16)));
    assertThat(JsonPath.<List<Boolean>>read(mine(ana, ""), "$.items[*].joinable"))
        .containsExactly(false, false);

    clock.set(start.minus(Duration.ofMinutes(10)));
    String list = mine(ana, "");
    assertThat(JsonPath.<List<String>>read(list, "$.items[*].bookingId"))
        .containsExactly(nine.toString(), eleven.toString());
    assertThat(JsonPath.<List<Boolean>>read(list, "$.items[*].joinable")).containsExactly(true, false);

    // While it is on, it can still be entered.
    clock.set(start.plus(Duration.ofMinutes(40)));
    assertThat(JsonPath.<List<Boolean>>read(mine(ana, ""), "$.items[*].joinable"))
        .containsExactly(true, false);
  }

  @Test
  @DisplayName("Consulting the history")
  void consultingTheHistory() throws Exception {
    UUID past = book(ana, tomorrowAt(9), 1, "Normal forms before Friday");
    UUID cancelled = book(ana, tomorrowAt(10), 1, "Joins before the lab");
    mockMvc
        .perform(as(ana, delete("/api/v1/bookings/" + cancelled)))
        .andExpect(status().isNoContent());
    UUID upcoming = book(ana, tomorrowAt(11), 1, "Indexes");

    clock.set(tomorrowAt(10).plus(Duration.ofMinutes(30)));
    // The nine o'clock hour is over and the ten o'clock one was cancelled.

    String ahead = mine(ana, "?view=UPCOMING");
    assertThat(JsonPath.<List<String>>read(ahead, "$.items[*].bookingId"))
        .containsExactly(upcoming.toString());
    String history = mine(ana, "?view=history");
    assertThat(JsonPath.<String>read(history, "$.view")).isEqualTo("HISTORY");
    assertThat(JsonPath.<List<String>>read(history, "$.items[*].bookingId"))
        .containsExactly(cancelled.toString(), past.toString());
    assertThat(JsonPath.<List<String>>read(history, "$.items[*].status"))
        .containsExactly("CANCELLED", "CONFIRMED");
    assertThat(JsonPath.<List<Boolean>>read(history, "$.items[*].joinable")).containsOnly(false);
  }

  @Test
  @DisplayName("Only my own tutorings of my university")
  void onlyMyOwnTutoringsOfMyUniversity() throws Exception {
    UUID mine = book(ana, tomorrowAt(9), 1, "Normal forms before Friday");
    book(bruno, tomorrowAt(10), 1, "Joins before the lab");
    // A booking of another university under Ana's identifier: the university of the session is
    // the one read, so it is not hers to see.
    jdbc.update(
        """
        insert into booking.bookings
          (id, tenant_id, student_id, tutor_id, catalog_item_id, starts_at, ends_at, hours,
           credits_charged, need_description, status)
        values (?, 'OTHER', ?, ?, ?, ?, ?, 1, 1, 'Elsewhere', 'CONFIRMED')
        """,
        UUID.randomUUID(),
        ana.id(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        Timestamp.from(tomorrowAt(12)),
        Timestamp.from(tomorrowAt(13)));

    String list = mine(ana, "");
    assertThat(JsonPath.<List<String>>read(list, "$.items[*].bookingId")).containsExactly(mine.toString());
    String history = mine(ana, "?view=HISTORY");
    assertThat(JsonPath.<List<Object>>read(history, "$.items")).isEmpty();
    assertThat(JsonPath.<Integer>read(history, "$.total")).isZero();

    // Without a session nobody is answered.
    mockMvc.perform(get("/api/v1/bookings/mine")).andExpect(status().isUnauthorized());
  }
}
