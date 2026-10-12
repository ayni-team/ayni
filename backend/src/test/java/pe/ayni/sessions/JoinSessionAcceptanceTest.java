package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.SessionStarted;

/**
 * US08 over HTTP against a real PostgreSQL, one test per scenario of {@code
 * features/US08-join-session.feature}.
 *
 * <p>Sessions are created the real way, by publishing the booking's {@code BookingConfirmed}.
 * Booking is the seam, mocked with the answer the real module gives for the need description.
 * Times are relative to now, so each test places its session where it needs it: about to start,
 * too far ahead, or already over.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(JoinSessionAcceptanceTest.StartedSessions.class)
class JoinSessionAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  private final UUID student = UUID.randomUUID();
  private final UUID tutor = UUID.randomUUID();
  private final UUID course = UUID.randomUUID();
  private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StartedSessions started;
  @MockitoBean private BookingApi booking;

  /** Counts SessionStarted per session, from whichever thread published it. */
  @TestConfiguration(proxyBeanMethods = false)
  static class StartedSessions {

    private final Map<UUID, AtomicInteger> counts = new ConcurrentHashMap<>();

    @EventListener
    void on(SessionStarted event) {
      counts.computeIfAbsent(event.sessionId(), id -> new AtomicInteger()).incrementAndGet();
    }

    int of(UUID sessionId) {
      return counts.getOrDefault(sessionId, new AtomicInteger()).get();
    }
  }

  @BeforeEach
  void bookingKnowsTheNeed() {
    when(booking.isConfirmed(any())).thenReturn(true);
    when(booking.requireBooking(any()))
        .thenAnswer(
            call ->
                new BookingView(
                    call.getArgument(0),
                    student,
                    tutor,
                    course,
                    now,
                    now.plus(Duration.ofHours(1)),
                    1,
                    "Normal forms before Friday's exam",
                    BookingStatus.CONFIRMED));
  }

  /** A confirmed booking starting that long from now, which schedules its session. */
  private UUID aSessionStartingIn(Duration fromNow) {
    Instant start = now.plus(fromNow);
    BookingConfirmed confirmed =
        new BookingConfirmed(
            UPC,
            UUID.randomUUID(),
            student,
            tutor,
            course,
            start,
            start.plus(Duration.ofHours(1)),
            List.of(UUID.randomUUID()),
            Credits.of(1),
            now);
    transactions.executeWithoutResult(status -> events.publishEvent(confirmed));
    return jdbc.queryForObject(
        "select id from sessions.sessions where tenant_id = ? and booking_id = ?",
        UUID.class,
        UPC,
        confirmed.bookingId());
  }

  private ResultActions read(UUID sessionId, UUID asUser) throws Exception {
    return mockMvc.perform(
        get("/api/v1/sessions/{id}", sessionId).header("X-Tenant-Id", UPC).header("X-User-Id", asUser));
  }

  private ResultActions join(UUID sessionId, UUID asUser) throws Exception {
    return join(UPC, sessionId, asUser);
  }

  private ResultActions join(String tenant, UUID sessionId, UUID asUser) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/{id}/join", sessionId)
            .header("X-Tenant-Id", tenant)
            .header("X-User-Id", asUser));
  }

  private String roomOf(UUID sessionId) {
    return jdbc.queryForObject(
        "select room_name from sessions.sessions where id = ?", String.class, sessionId);
  }

  private int participationsOf(UUID sessionId) {
    return jdbc.queryForObject(
        "select count(*) from sessions.participations where tenant_id = ? and session_id = ?",
        Integer.class,
        UPC,
        sessionId);
  }

  @Test
  @DisplayName("The tutor reads the session and what the student needs")
  void theTutorReadsTheSessionAndTheNeed() throws Exception {

    UUID session = aSessionStartingIn(Duration.ofHours(2));

    read(session, tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(session.toString()))
        .andExpect(jsonPath("$.role").value("TUTOR"))
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.catalogItemId").value(course.toString()))
        .andExpect(jsonPath("$.needDescription").value("Normal forms before Friday's exam"))
        .andExpect(
            jsonPath("$.joinOpensAt")
                .value(now.plus(Duration.ofHours(2)).minus(Duration.ofMinutes(15)).toString()))
        // The room is only handed over by joining.
        .andExpect(jsonPath("$.roomName").doesNotExist());

    read(session, student).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
  }

  @Test
  @DisplayName("Somebody else cannot see the session or join it")
  void somebodyElseIsRefused() throws Exception {

    UUID session = aSessionStartingIn(Duration.ofMinutes(10));
    UUID stranger = UUID.randomUUID();

    read(session, stranger)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("Forbidden"))
        .andExpect(jsonPath("$.message", containsString("Only the student and the tutor")))
        .andExpect(jsonPath("$.path").value("/api/v1/sessions/" + session));
    join(session, stranger).andExpect(status().isForbidden());

    assertThat(participationsOf(session)).isZero();
    assertThat(started.of(session)).isZero();
  }

  @Test
  @DisplayName("Joining opens the room and the first participant starts the session")
  void joiningStartsTheSession() throws Exception {

    UUID session = aSessionStartingIn(Duration.ofMinutes(10));

    join(session, student)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("STUDENT"))
        .andExpect(jsonPath("$.roomName").value(roomOf(session)))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.startedAt").exists());
    String startedAt =
        jdbc.queryForObject(
            "select started_at::text from sessions.sessions where id = ?", String.class, session);

    join(session, tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("TUTOR"))
        .andExpect(jsonPath("$.roomName").value(roomOf(session)));

    assertThat(started.of(session)).as("SessionStarted, once").isEqualTo(1);
    assertThat(participationsOf(session)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select started_at::text from sessions.sessions where id = ?", String.class, session))
        .as("the start is the first arrival")
        .isEqualTo(startedAt);
  }

  @Test
  @DisplayName("Coming back after a dropped connection")
  void comingBackChangesNothing() throws Exception {

    UUID session = aSessionStartingIn(Duration.ofMinutes(5));

    join(session, student).andExpect(status().isOk());
    String firstArrival =
        jdbc.queryForObject(
            "select joined_at::text from sessions.participations where session_id = ? and user_id = ?",
            String.class,
            session,
            student);
    join(session, student).andExpect(status().isOk()).andExpect(jsonPath("$.roomName").exists());

    assertThat(participationsOf(session)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select joined_at::text from sessions.participations where session_id = ? and user_id = ?",
                String.class,
                session,
                student))
        .isEqualTo(firstArrival);
    assertThat(started.of(session)).isEqualTo(1);
  }

  @Test
  @DisplayName("Both arriving at the same moment start the session once")
  void bothAtOnceStartItOnce() throws Exception {

    UUID session = aSessionStartingIn(Duration.ofMinutes(5));
    CyclicBarrier bothReady = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> answers = new ArrayList<>();
      for (UUID participant : List.of(student, tutor)) {
        answers.add(
            pool.submit(
                () -> {
                  bothReady.await();
                  return join(session, participant).andReturn().getResponse().getStatus();
                }));
      }
      for (Future<Integer> answer : answers) {
        assertThat(answer.get()).isEqualTo(200);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(started.of(session)).isEqualTo(1);
    assertThat(participationsOf(session)).isEqualTo(2);
  }

  @Test
  @DisplayName("The room is not open too early, after the end, or for a cancelled session")
  void theRoomIsNotOpenOutsideItsWindow() throws Exception {

    UUID tooEarly = aSessionStartingIn(Duration.ofHours(1));
    join(tooEarly, student)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("opens fifteen minutes before")));

    UUID over = aSessionStartingIn(Duration.ofHours(-2));
    join(over, tutor)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("This session has already ended"));

    UUID cancelled = aSessionStartingIn(Duration.ofMinutes(5));
    jdbc.update("update sessions.sessions set status = 'CANCELLED' where id = ?", cancelled);
    join(cancelled, student)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("cancelled")));

    assertThat(participationsOf(tooEarly) + participationsOf(over) + participationsOf(cancelled))
        .isZero();
  }

  @Test
  @DisplayName("A session of another university, or one that does not exist, is not found")
  void anotherUniversitysSessionIsNotFound() throws Exception {

    UUID session = aSessionStartingIn(Duration.ofMinutes(5));

    join("PUCP", session, student).andExpect(status().isNotFound());
    join(UUID.randomUUID(), student).andExpect(status().isNotFound());
    mockMvc
        .perform(post("/api/v1/sessions/{id}/join", session).header("X-Tenant-Id", UPC))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("X-User-Id")));
  }
}
