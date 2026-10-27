package pe.ayni.sessions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;

/**
 * US12 over HTTP against a real PostgreSQL. One test per scenario of {@code
 * features/US12-review-a-past-session.feature}, with the same name.
 *
 * <p>The sessions are written straight in their tables in the state each scenario needs: how they
 * got there is the business of the stories that move them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewPastSessionAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final String NEED = "Recursión con listas antes del parcial";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private BookingApi booking;

  private final UUID student = UUID.randomUUID();
  private final UUID tutor = UUID.randomUUID();
  private final Instant start =
      Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(Duration.ofDays(3));

  @BeforeEach
  void bookingKnowsTheNeed() {
    when(booking.requireBooking(any()))
        .thenAnswer(
            call ->
                new BookingView(
                    call.getArgument(0),
                    student,
                    tutor,
                    UUID.randomUUID(),
                    start,
                    start.plus(Duration.ofHours(1)),
                    1,
                    NEED,
                    BookingStatus.COMPLETED));
  }

  private UUID aSession(String status, Instant startedAt, Instant endedAt) {
    UUID session = UUID.randomUUID();
    jdbc.update(
        """
        insert into sessions.sessions
          (id, tenant_id, booking_id, student_id, tutor_id, scheduled_start, scheduled_end,
           room_name, started_at, ended_at, status)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        session,
        UPC,
        UUID.randomUUID(),
        student,
        tutor,
        Timestamp.from(start),
        Timestamp.from(start.plus(Duration.ofHours(1))),
        "room-" + session,
        startedAt == null ? null : Timestamp.from(startedAt),
        endedAt == null ? null : Timestamp.from(endedAt),
        status);
    return session;
  }

  private void arrived(UUID session, UUID who, Instant at) {
    jdbc.update(
        """
        insert into sessions.participations (id, tenant_id, session_id, user_id, role, joined_at)
        values (?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        UPC,
        session,
        who,
        who.equals(student) ? "STUDENT" : "TUTOR",
        Timestamp.from(at));
  }

  private ResultActions review(UUID session, UUID who) throws Exception {
    return review(session, who, UPC);
  }

  private ResultActions review(UUID session, UUID who, String tenant) throws Exception {
    return mockMvc.perform(
        get("/api/v1/sessions/{id}/review", session)
            .header("X-Tenant-Id", tenant)
            .header("X-User-Id", who.toString()));
  }

  @Test
  @DisplayName("A participant reviews a session they took")
  void aParticipantReviewsASessionTheyTook() throws Exception {
    Instant began = start.plus(Duration.ofMinutes(3));
    UUID session = aSession("COMPLETED", began, began.plus(Duration.ofMinutes(52)));
    jdbc.update(
        """
        insert into sessions.whiteboards (id, tenant_id, session_id, content, has_activity, updated_at)
        values (?, ?, ?, ?::jsonb, true, ?)
        """,
        UUID.randomUUID(),
        UPC,
        session,
        """
        {"version": 7, "elements": [
          {"id": "tree", "type": "stroke", "data": {"points": [[0, 0], [10, 10]]}},
          {"id": "base-case", "type": "text", "data": {"text": "n = 0"}}]}
        """,
        Timestamp.from(began.plus(Duration.ofMinutes(50))));
    jdbc.update(
        """
        insert into sessions.support_materials (id, tenant_id, session_id, summary, suggestions)
        values (?, ?, ?, ?, '["Prepara un ejemplo resuelto sobre recursión"]'::jsonb)
        """,
        UUID.randomUUID(),
        UPC,
        session,
        NEED);

    for (UUID participant : new UUID[] {student, tutor}) {
      review(session, participant)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("COMPLETED"))
          .andExpect(jsonPath("$.statusNote").exists())
          .andExpect(jsonPath("$.bookedMinutes").value(60))
          .andExpect(jsonPath("$.realDurationMinutes").value(52))
          .andExpect(jsonPath("$.startedAt").value(began.toString()))
          .andExpect(jsonPath("$.needDescription").value(NEED))
          .andExpect(jsonPath("$.supportMaterial.summary").value(NEED))
          .andExpect(
              jsonPath("$.supportMaterial.suggestions[0]")
                  .value("Prepara un ejemplo resuelto sobre recursión"))
          .andExpect(jsonPath("$.whiteboard.version").value(7))
          .andExpect(jsonPath("$.whiteboard.hasActivity").value(true))
          .andExpect(jsonPath("$.whiteboard.live").value(false))
          .andExpect(jsonPath("$.whiteboard.elements[0].id").value("tree"))
          .andExpect(jsonPath("$.whiteboard.elements[1].data.text").value("n = 0"));
    }
  }

  @Test
  @DisplayName("A cancelled session shows that it was cancelled")
  void aCancelledSessionShowsThatItWasCancelled() throws Exception {
    UUID session = aSession("CANCELLED", null, start.minus(Duration.ofDays(1)));

    review(session, student)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(
            jsonPath("$.statusNote").value("The booking was cancelled before the session started"))
        .andExpect(jsonPath("$.realDurationMinutes").doesNotExist())
        .andExpect(jsonPath("$.whiteboard").doesNotExist())
        .andExpect(jsonPath("$.needDescription").value(NEED));
  }

  @Test
  @DisplayName("A session that did not start shows why")
  void aSessionThatDidNotStartShowsWhy() throws Exception {
    UUID session = aSession("ABANDONED", null, start.plus(Duration.ofMinutes(10)));
    arrived(session, student, start.minus(Duration.ofMinutes(2)));

    review(session, student)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ABANDONED"))
        .andExpect(
            jsonPath("$.statusNote")
                .value("The session did not start: a participant had not come ten minutes in"))
        .andExpect(jsonPath("$.absentParticipantIds[0]").value(tutor.toString()))
        .andExpect(jsonPath("$.absentParticipantIds.length()").value(1))
        .andExpect(jsonPath("$.startedAt").doesNotExist())
        .andExpect(jsonPath("$.realDurationMinutes").doesNotExist())
        .andExpect(jsonPath("$.whiteboard").doesNotExist());
  }

  @Test
  @DisplayName("Somebody who did not take part is refused")
  void somebodyWhoDidNotTakePartIsRefused() throws Exception {
    Instant began = start.plus(Duration.ofMinutes(1));
    UUID session = aSession("COMPLETED", began, began.plus(Duration.ofMinutes(55)));

    review(session, UUID.randomUUID())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.whiteboard").doesNotExist());
    review(session, student, "PUCP").andExpect(status().isNotFound());
    review(UUID.randomUUID(), student).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A session nobody drew on shows an empty board marked without activity")
  void aSessionNobodyDrewOnShowsAnEmptyBoard() throws Exception {
    Instant began = start.plus(Duration.ofMinutes(2));
    UUID session = aSession("UNVERIFIED", began, began.plus(Duration.ofMinutes(73)));
    jdbc.update(
        """
        insert into sessions.whiteboards (id, tenant_id, session_id, has_activity, updated_at)
        values (?, ?, ?, false, now())
        """,
        UUID.randomUUID(),
        UPC,
        session);

    review(session, tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNVERIFIED"))
        .andExpect(jsonPath("$.realDurationMinutes").value(73))
        .andExpect(jsonPath("$.whiteboard.elements").isEmpty())
        .andExpect(jsonPath("$.whiteboard.version").value(0))
        .andExpect(jsonPath("$.whiteboard.hasActivity").value(false))
        .andExpect(jsonPath("$.supportMaterial").doesNotExist());
  }
}
