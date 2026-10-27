package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US09 over HTTP against a real PostgreSQL, one test per scenario of {@code
 * features/US09-mutual-session-check-in.feature}, with the display name of the scenario.
 *
 * <p>Ana books the hour the real way and the clock is the one the tests move, so each scenario
 * puts every arrival at the minute it needs. The job is not waited for: the tests call the use case
 * it calls. Wallet, booking, sessions and notifications are the real modules. Who pays for an
 * absence is US06's and is shown by {@link TutorNoShowAcceptanceTest}; here the session and its
 * record are what is looked at.
 */
class MutualCheckInAcceptanceTest extends TutorNoShowScenario {

  @Test
  @DisplayName("Both participants check in and the session starts, with the time of each arrival")
  void bothCheckInAndTheSessionStarts() throws Exception {

    UUID session = sessionOf(anaBooksTheHour());
    Instant anaArrived = start.minus(Duration.ofMinutes(5));
    Instant brunoArrived = start.minus(Duration.ofMinutes(2));

    clock.set(anaArrived);
    join(session, ana)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.startedAt").doesNotExist())
        .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()))
        .andExpect(jsonPath("$.tutorJoinedAt").doesNotExist());
    assertThat(sessionStatus(session)).isEqualTo("SCHEDULED");
    assertThat(announcements.startedOf(session)).isEmpty();
    assertThat(arrivalOf(session, ana)).isEqualTo(anaArrived);

    clock.set(brunoArrived);
    join(session, tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.startedAt").value(brunoArrived.toString()))
        .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()))
        .andExpect(jsonPath("$.tutorJoinedAt").value(brunoArrived.toString()));

    assertThat(sessionStatus(session)).isEqualTo("IN_PROGRESS");
    assertThat(startedAtOf(session)).as("the second arrival is the start").isEqualTo(brunoArrived);
    assertThat(arrivalOf(session, ana)).isEqualTo(anaArrived);
    assertThat(arrivalOf(session, tutor)).isEqualTo(brunoArrived);
    assertThat(announcements.startedOf(session)).hasSize(1);
    assertThat(announcements.startedOf(session).getFirst().occurredOn()).isEqualTo(brunoArrived);
  }

  @Test
  @DisplayName("Whoever enters sees that the other person already arrived")
  void whoeverEntersSeesTheOtherAlreadyArrived() throws Exception {

    UUID session = sessionOf(anaBooksTheHour());
    Instant anaArrived = start.minus(Duration.ofMinutes(5));
    Instant brunoArrived = start.minus(Duration.ofMinutes(3));

    clock.set(anaArrived);
    join(session, ana).andExpect(status().isOk());

    // Bruno looks before he goes in: Ana is there, he is not.
    clock.set(start.minus(Duration.ofMinutes(4)));
    read(session, tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()))
        .andExpect(jsonPath("$.tutorJoinedAt").doesNotExist());

    clock.set(brunoArrived);
    join(session, tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()))
        .andExpect(jsonPath("$.tutorJoinedAt").value(brunoArrived.toString()));

    read(session, ana)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()))
        .andExpect(jsonPath("$.tutorJoinedAt").value(brunoArrived.toString()))
        .andExpect(jsonPath("$.absentParticipantIds").isEmpty());
  }

  @Test
  @DisplayName(
      "Only one participant is present at ten minutes, so the session does not start and who was"
          + " absent is recorded")
  void onlyOneIsPresentAtTenMinutes() throws Exception {

    grant(ana, 5);
    clock.set(start.minus(Duration.ofDays(1)));
    UUID tutorMissing = sessionOf(bookHour(start));
    UUID studentMissing = sessionOf(bookHour(start.plus(Duration.ofHours(1))));
    Instant firstDeadline = start.plus(Duration.ofMinutes(10));
    Instant secondDeadline = firstDeadline.plus(Duration.ofHours(1));

    // The tutor is the one who does not come: Ana waits in the room.
    clock.set(start.minus(Duration.ofMinutes(5)));
    join(tutorMissing, ana).andExpect(status().isOk());
    // The student is the one who does not come: Bruno waits in the room.
    clock.set(start.plus(Duration.ofHours(1)).minus(Duration.ofMinutes(5)));
    join(studentMissing, tutor).andExpect(status().isOk());

    // One second before the mark, nothing is decided yet.
    clock.set(firstDeadline.minusSeconds(1));
    TenantContext.runAs(
        UPC,
        () -> {
          assertThat(abandonNoShows.dueNow()).doesNotContain(tutorMissing);
          assertThat(abandonNoShows.abandonFor(tutorMissing)).isFalse();
        });
    assertThat(sessionStatus(tutorMissing)).isEqualTo("SCHEDULED");

    clock.set(firstDeadline);
    assertThat(abandonDue(tutorMissing)).isTrue();
    assertThat(sessionStatus(tutorMissing)).isEqualTo("ABANDONED");
    assertThat(startedAtOf(tutorMissing)).as("it never started").isNull();
    assertThat(announcements.startedOf(tutorMissing)).isEmpty();
    for (UUID reader : new UUID[] {ana, tutor}) {
      read(tutorMissing, reader)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ABANDONED"))
          .andExpect(jsonPath("$.startedAt").doesNotExist())
          .andExpect(jsonPath("$.studentJoinedAt").value(start.minus(Duration.ofMinutes(5)).toString()))
          .andExpect(jsonPath("$.tutorJoinedAt").doesNotExist())
          .andExpect(jsonPath("$.absentParticipantIds.length()").value(1))
          .andExpect(jsonPath("$.absentParticipantIds[0]").value(tutor.toString()));
    }

    // The other way around, an hour later.
    clock.set(secondDeadline.minusSeconds(1));
    TenantContext.runAs(
        UPC, () -> assertThat(abandonNoShows.abandonFor(studentMissing)).isFalse());
    clock.set(secondDeadline);
    assertThat(abandonDue(studentMissing)).isTrue();
    assertThat(sessionStatus(studentMissing)).isEqualTo("ABANDONED");
    assertThat(startedAtOf(studentMissing)).as("it never started").isNull();
    assertThat(announcements.startedOf(studentMissing)).isEmpty();
    for (UUID reader : new UUID[] {ana, tutor}) {
      read(studentMissing, reader)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ABANDONED"))
          .andExpect(jsonPath("$.startedAt").doesNotExist())
          .andExpect(jsonPath("$.studentJoinedAt").doesNotExist())
          .andExpect(
              jsonPath("$.tutorJoinedAt")
                  .value(start.plus(Duration.ofHours(1)).minus(Duration.ofMinutes(5)).toString()))
          .andExpect(jsonPath("$.absentParticipantIds.length()").value(1))
          .andExpect(jsonPath("$.absentParticipantIds[0]").value(ana.toString()));
    }
    assertThat(bookingStatusOfSession(tutorMissing)).isEqualTo("NO_SHOW");
    assertThat(bookingStatusOfSession(studentMissing)).isEqualTo("NO_SHOW");
  }

  @Test
  @DisplayName("The arrival record can be consulted and cannot be modified")
  void theArrivalRecordIsConsultableAndNotModifiable() throws Exception {

    UUID session = sessionOf(anaBooksTheHour());
    Instant anaArrived = start.minus(Duration.ofMinutes(5));
    Instant brunoArrived = start.minus(Duration.ofMinutes(2));
    clock.set(anaArrived);
    join(session, ana).andExpect(status().isOk());
    clock.set(brunoArrived);
    join(session, tutor).andExpect(status().isOk());

    // Either of them can read both arrivals.
    for (UUID reader : new UUID[] {ana, tutor}) {
      read(session, reader)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()))
          .andExpect(jsonPath("$.tutorJoinedAt").value(brunoArrived.toString()));
    }
    // Somebody else cannot.
    read(session, UUID.randomUUID()).andExpect(status().isForbidden());

    // Coming back later, from either side, does not move anything.
    clock.set(start.plus(Duration.ofMinutes(30)));
    join(session, ana)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()))
        .andExpect(jsonPath("$.tutorJoinedAt").value(brunoArrived.toString()))
        .andExpect(jsonPath("$.startedAt").value(brunoArrived.toString()));
    join(session, tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()))
        .andExpect(jsonPath("$.tutorJoinedAt").value(brunoArrived.toString()));

    // A request that tries to say when somebody arrived is not listened to.
    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/join", session)
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", ana)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"studentJoinedAt":"%s","tutorJoinedAt":"%s","joinedAt":"%s"}
                    """
                        .formatted(start, start, start)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()));

    // And there is nothing to change it with: the session and its arrivals take no write.
    for (String path : new String[] {"/api/v1/sessions/" + session,
        "/api/v1/sessions/" + session + "/arrivals",
        "/api/v1/sessions/" + session + "/join"}) {
      for (var request :
          new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
            put(path), patch(path), delete(path)
          }) {
        mockMvc
            .perform(
                request
                    .header("X-Tenant-Id", UPC)
                    .header("X-User-Id", ana)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"studentJoinedAt\":\"" + start + "\"}"))
            .andExpect(status().is4xxClientError());
      }
    }
    assertThat(arrivalOf(session, ana)).isEqualTo(anaArrived);
    assertThat(arrivalOf(session, tutor)).isEqualTo(brunoArrived);

    // After the session ended, the record is still there, as it was.
    end(session, ana).andExpect(status().isOk());
    end(session, tutor).andExpect(status().isOk());
    assertThat(sessionStatus(session)).isNotIn("SCHEDULED", "IN_PROGRESS");
    read(session, tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()))
        .andExpect(jsonPath("$.tutorJoinedAt").value(brunoArrived.toString()));
    assertThat(arrivalOf(session, ana)).isEqualTo(anaArrived);
    assertThat(arrivalOf(session, tutor)).isEqualTo(brunoArrived);
  }

  @Test
  @DisplayName("Arriving at the ten minute mark is late: the arrival is recorded and nothing starts")
  void anArrivalAtTheMarkIsLate() throws Exception {

    UUID bookingId = anaBooksTheHour();
    UUID session = sessionOf(bookingId);
    anaCheckedIn(session);
    Instant deadline = start.plus(Duration.ofMinutes(10));

    clock.set(deadline);
    join(session, tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.tutorJoinedAt").value(deadline.toString()));
    assertThat(announcements.startedOf(session)).isEmpty();

    assertThat(abandonDue(session)).isTrue();
    assertThat(sessionStatus(session)).isEqualTo("ABANDONED");
    // The late arrival is on the record, and the tutor is still the one who was absent: Ana is
    // refunded and the incident is theirs.
    read(session, ana)
        .andExpect(jsonPath("$.tutorJoinedAt").value(deadline.toString()))
        .andExpect(jsonPath("$.absentParticipantIds[0]").value(tutor.toString()));
    assertThat(refundsOf(bookingId)).isEqualTo(1);
    assertThat(incidentsOf(bookingId, "NO_SHOW")).isEqualTo(1);
  }

  /** Books another hour with the tutor for Ana, the real way, and returns its booking. */
  private UUID bookHour(Instant hourStart) throws Exception {
    hold(ana, hourStart, 1).andExpect(status().isCreated());
    String answer =
        book(ana, hourStart, 1, "Normal forms")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(answer, "$.id"));
  }

  private ResultActions read(UUID sessionId, UUID asUser) throws Exception {
    return mockMvc.perform(
        get("/api/v1/sessions/{id}", sessionId)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", asUser));
  }

  private ResultActions end(UUID sessionId, UUID asUser) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/{id}/end", sessionId)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", asUser));
  }

  private Instant arrivalOf(UUID sessionId, UUID participant) {
    return jdbc
        .queryForObject(
            """
            select joined_at from sessions.participations
            where tenant_id = ? and session_id = ? and user_id = ?
            """,
            Timestamp.class,
            UPC,
            sessionId,
            participant)
        .toInstant();
  }

  private Instant startedAtOf(UUID sessionId) {
    Timestamp startedAt =
        jdbc.queryForObject(
            "select started_at from sessions.sessions where id = ?", Timestamp.class, sessionId);
    return startedAt == null ? null : startedAt.toInstant();
  }

  private String bookingStatusOfSession(UUID sessionId) {
    return jdbc.queryForObject(
        """
        select booking.status from booking.bookings booking
        join sessions.sessions session on session.booking_id = booking.id
        where session.id = ?
        """,
        String.class,
        sessionId);
  }
}
