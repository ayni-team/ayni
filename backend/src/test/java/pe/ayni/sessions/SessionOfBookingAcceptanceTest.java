package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * Finding the session of a booking over HTTP against a real PostgreSQL, with real session tokens
 * (TS03). The two scenarios named "The highlighted tutoring opens its session" are the ones of
 * {@code features/US04-upcoming-tutorings.feature} (the student) and {@code
 * features/US21-tutor-agenda.feature} (the tutor): the list only knows the booking, so the person
 * asks sessions for its session and then joins it.
 *
 * <p>The sessions are written in their table: how a confirmed booking becomes one is US03's
 * business. Times are relative to now, since joining reads the clock of the application.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class SessionOfBookingAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  private SignedInPeople people() {
    return new SignedInPeople(tenants, users, userSessions, tokens);
  }

  @Test
  @DisplayName("The highlighted tutoring opens its session")
  void theHighlightedTutoringOpensItsSessionForTheStudent() throws Exception {
    SignedInPeople people = people();
    String tenant = people.aUniversity("K");
    Person student = people.aStudent(tenant);
    Person tutor = people.aStudent(tenant);
    // Given one of my tutorings starts in less than fifteen minutes
    UUID booking = UUID.randomUUID();
    UUID session = aSession(tenant, booking, student, tutor, Duration.ofMinutes(10));

    // When I choose it in the list, which only knows the booking
    String answer =
        sessionOf(booking, student)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value(session.toString()))
            .andExpect(jsonPath("$.bookingId").value(booking.toString()))
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then I enter its session
    assertThat(Instant.parse(JsonPath.read(answer, "$.joinOpensAt"))).isBeforeOrEqualTo(Instant.now());
    join(JsonPath.read(answer, "$.sessionId"), student)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value(session.toString()))
        .andExpect(jsonPath("$.roomName").exists())
        .andExpect(jsonPath("$.studentJoinedAt").exists());
  }

  @Test
  @DisplayName("The highlighted tutoring opens its session")
  void theHighlightedTutoringOpensItsSessionForTheTutor() throws Exception {
    SignedInPeople people = people();
    String tenant = people.aUniversity("K");
    Person student = people.aStudent(tenant);
    Person tutor = people.aStudent(tenant);
    // Given one of my tutorings starts in less than fifteen minutes
    UUID booking = UUID.randomUUID();
    UUID session = aSession(tenant, booking, student, tutor, Duration.ofMinutes(10));

    // When I choose it in my agenda, which only knows the booking
    String answer =
        sessionOf(booking, tutor)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value(session.toString()))
            .andExpect(jsonPath("$.bookingId").value(booking.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then I enter its session
    assertThat(Instant.parse(JsonPath.read(answer, "$.joinOpensAt"))).isBeforeOrEqualTo(Instant.now());
    join(JsonPath.read(answer, "$.sessionId"), tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value(session.toString()))
        .andExpect(jsonPath("$.tutorJoinedAt").exists());
  }

  @Test
  @DisplayName("The session of a booking says when its room opens and when it ends")
  void theSessionOfABookingSaysWhenItsRoomOpensAndWhenItEnds() throws Exception {
    SignedInPeople people = people();
    String tenant = people.aUniversity("K");
    Person student = people.aStudent(tenant);
    Person tutor = people.aStudent(tenant);
    UUID booking = UUID.randomUUID();
    Instant start = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
    aSessionAt(tenant, booking, student, tutor, start, Duration.ofHours(2));

    sessionOf(booking, student)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.joinOpensAt").value(start.minus(Duration.ofMinutes(15)).toString()))
        .andExpect(jsonPath("$.scheduledEnd").value(start.plus(Duration.ofHours(2)).toString()))
        .andExpect(jsonPath("$.roomName").doesNotExist());
  }

  @Test
  @DisplayName("Somebody who is neither the student nor the tutor is refused")
  void somebodyWhoIsNeitherTheStudentNorTheTutorIsRefused() throws Exception {
    SignedInPeople people = people();
    String tenant = people.aUniversity("K");
    Person student = people.aStudent(tenant);
    Person tutor = people.aStudent(tenant);
    Person stranger = people.aStudent(tenant);
    UUID booking = UUID.randomUUID();
    aSession(tenant, booking, student, tutor, Duration.ofMinutes(10));

    sessionOf(booking, stranger).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("The session of another university is not found")
  void theSessionOfAnotherUniversityIsNotFound() throws Exception {
    SignedInPeople people = people();
    String tenant = people.aUniversity("K");
    String other = people.aUniversity("L");
    Person student = people.aStudent(tenant);
    Person tutor = people.aStudent(tenant);
    Person outsider = people.aStudent(other);
    UUID booking = UUID.randomUUID();
    aSession(tenant, booking, student, tutor, Duration.ofMinutes(10));

    sessionOf(booking, outsider).andExpect(status().isNotFound());
    // The same booking is found by the student of the university that holds it.
    sessionOf(booking, student).andExpect(status().isOk());
  }

  @Test
  @DisplayName("A booking without a session is not found")
  void aBookingWithoutASessionIsNotFound() throws Exception {
    SignedInPeople people = people();
    String tenant = people.aUniversity("K");
    Person student = people.aStudent(tenant);

    sessionOf(UUID.randomUUID(), student)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.path").value("/api/v1/sessions"));
  }

  @Test
  @DisplayName("A request without a booking, or with one that is not an identifier, is malformed")
  void aRequestWithoutABookingOrWithOneThatIsNotAnIdentifierIsMalformed() throws Exception {
    SignedInPeople people = people();
    Person student = people.aStudent(people.aUniversity("K"));

    mockMvc
        .perform(get("/api/v1/sessions").header("Authorization", "Bearer " + student.token()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("The parameter bookingId is required"));
    mockMvc
        .perform(
            get("/api/v1/sessions")
                .param("bookingId", "not-a-uuid")
                .header("Authorization", "Bearer " + student.token()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The value of bookingId could not be read"));
  }

  @Test
  @DisplayName("Asking for the session of a booking needs to be signed in")
  void askingForTheSessionOfABookingNeedsToBeSignedIn() throws Exception {
    mockMvc
        .perform(get("/api/v1/sessions").param("bookingId", UUID.randomUUID().toString()))
        .andExpect(status().isUnauthorized());
  }

  private ResultActions sessionOf(UUID booking, Person who) throws Exception {
    return mockMvc.perform(
        get("/api/v1/sessions")
            .param("bookingId", booking.toString())
            .header("Authorization", "Bearer " + who.token()));
  }

  private ResultActions join(String session, Person who) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/" + session + "/join")
            .header("Authorization", "Bearer " + who.token()));
  }

  private UUID aSession(String tenant, UUID booking, Person student, Person tutor, Duration startsIn) {
    return aSessionAt(
        tenant,
        booking,
        student,
        tutor,
        Instant.now().plus(startsIn).truncatedTo(ChronoUnit.SECONDS),
        Duration.ofHours(1));
  }

  private UUID aSessionAt(
      String tenant, UUID booking, Person student, Person tutor, Instant start, Duration length) {
    UUID session = UUID.randomUUID();
    jdbc.update(
        """
        insert into sessions.sessions
          (id, tenant_id, booking_id, student_id, tutor_id, scheduled_start, scheduled_end,
           room_name, status)
        values (?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED')
        """,
        session,
        tenant,
        booking,
        student.id(),
        tutor.id(),
        Timestamp.from(start),
        Timestamp.from(start.plus(length)),
        "room-" + session);
    return session;
  }
}
