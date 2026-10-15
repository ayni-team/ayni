package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * The scenario of {@code features/US09-mutual-session-check-in.feature} about the database and the
 * arrival times, with the same name: both participants check in over HTTP with real session tokens
 * (TS03), and then somebody tries to rewrite an arrival straight in PostgreSQL.
 *
 * <p>The session is written in its table, scheduled to have started two minutes ago: how it was
 * booked is US03's business. The arrivals are recorded by the real endpoint.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class ArrivalRecordAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @MockitoBean private BookingApi booking;

  @Test
  @DisplayName("The database refuses to change an arrival time")
  void theDatabaseRefusesToChangeAnArrivalTime() throws Exception {
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    String tenant = people.aUniversity("J");
    Person ana = people.aStudent(tenant);
    Person bruno = people.aStudent(tenant);
    UUID session = aSessionThatStartedTwoMinutesAgo(tenant, ana.id(), bruno.id());
    when(booking.requireBooking(any(UUID.class)))
        .thenAnswer(
            call ->
                new BookingView(
                    call.getArgument(0), ana.id(), bruno.id(), UUID.randomUUID(), Instant.now(),
                    Instant.now().plus(Duration.ofHours(1)), 1, "Normal forms", BookingStatus.CONFIRMED));

    // Given both participants checked in
    join(session, ana);
    join(session, bruno);
    Instant anaArrived = arrivalOf(session, ana);
    Instant brunoArrived = arrivalOf(session, bruno);
    assertThat(anaArrived).isNotNull();
    assertThat(brunoArrived).isNotNull();

    // When anybody updates the arrival time of a participation directly in the database
    Timestamp earlier = Timestamp.from(anaArrived.minus(Duration.ofMinutes(30)));
    // Then the update is refused
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update sessions.participations set joined_at = ? where session_id = ? and user_id = ?",
                    earlier,
                    session,
                    ana.id()))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("joined_at is recorded once");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update sessions.participations set joined_at = null where session_id = ?", session))
        .isInstanceOf(DataAccessException.class);

    // The rest of the row still changes, and writing the same arrival again is not a change.
    assertThat(
            jdbc.update(
                "update sessions.participations set end_confirmed_at = now(), joined_at = joined_at"
                    + " where session_id = ? and user_id = ?",
                session,
                bruno.id()))
        .isEqualTo(1);
    // Both participants still read the arrivals as they were recorded.
    mockMvc
        .perform(get("/api/v1/sessions/" + session).header("Authorization", "Bearer " + bruno.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentJoinedAt").value(anaArrived.toString()))
        .andExpect(jsonPath("$.tutorJoinedAt").value(brunoArrived.toString()));
    assertThat(arrivalOf(session, ana)).isEqualTo(anaArrived);
  }

  private void join(UUID session, Person participant) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sessions/" + session + "/join")
                .header("Authorization", "Bearer " + participant.token()))
        .andExpect(status().isOk());
  }

  private UUID aSessionThatStartedTwoMinutesAgo(String tenant, UUID student, UUID tutor) {
    UUID session = UUID.randomUUID();
    Instant start = Instant.now().minus(Duration.ofMinutes(2)).truncatedTo(ChronoUnit.SECONDS);
    jdbc.update(
        """
        insert into sessions.sessions
          (id, tenant_id, booking_id, student_id, tutor_id, scheduled_start, scheduled_end,
           room_name, status)
        values (?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED')
        """,
        session,
        tenant,
        UUID.randomUUID(),
        student,
        tutor,
        Timestamp.from(start),
        Timestamp.from(start.plus(Duration.ofHours(1))),
        "room-" + session);
    return session;
  }

  private Instant arrivalOf(UUID session, Person participant) {
    return jdbc.queryForObject(
            "select joined_at from sessions.participations where session_id = ? and user_id = ?",
            Timestamp.class,
            session,
            participant.id())
        .toInstant();
  }
}
