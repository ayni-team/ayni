package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.sessions.application.CloseOverdueSessionsUseCase;
import pe.ayni.sessions.application.IssuePresenceCodesUseCase;
import pe.ayni.sessions.application.ScheduleSessionUseCase;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;

/**
 * US11 over HTTP against a real PostgreSQL, one test per scenario of {@code
 * features/US11-close-session.feature}, with scenario 3 of US54 as the unverified outcome.
 *
 * <p>Sessions are scheduled with the use case booking's event calls, joined and ended over HTTP,
 * and their presence proved with the codes read from {@link PresenceCodeIssued}. The overdue ones
 * are closed by calling the use case the job calls. Wallet is the real one, so each test also shows
 * the money: the tutor paid, or the student refunded. Booking only answers the course.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CloseSessionAcceptanceTest.Outcomes.class)
class CloseSessionAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  private final UUID student = UUID.randomUUID();
  private final UUID tutor = UUID.randomUUID();
  private final UUID course = UUID.randomUUID();
  private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ScheduleSessionUseCase scheduleSession;
  @Autowired private IssuePresenceCodesUseCase issuePresenceCodes;
  @Autowired private CloseOverdueSessionsUseCase closeOverdueSessions;
  @Autowired private WalletApi wallet;
  @Autowired private Outcomes outcomes;
  @MockitoBean private BookingApi booking;
  @MockitoBean private IdentityApi identity;

  /** What sessions announced: the codes it sent, and how each session ended. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Outcomes {

    private final Map<UUID, Map<UUID, String>> codes = new ConcurrentHashMap<>();
    private final List<SessionCompleted> completed = new CopyOnWriteArrayList<>();
    private final List<SessionUnverified> unverified = new CopyOnWriteArrayList<>();

    @EventListener
    void on(PresenceCodeIssued event) {
      codes.computeIfAbsent(event.sessionId(), id -> new ConcurrentHashMap<>())
          .put(event.userId(), event.code());
    }

    @EventListener
    void on(SessionCompleted event) {
      completed.add(event);
    }

    @EventListener
    void on(SessionUnverified event) {
      unverified.add(event);
    }

    String codeOf(UUID sessionId, UUID participant) {
      return codes.get(sessionId).get(participant);
    }

    List<SessionCompleted> completedOf(UUID sessionId) {
      return completed.stream().filter(event -> event.sessionId().equals(sessionId)).toList();
    }

    List<SessionUnverified> unverifiedOf(UUID sessionId) {
      return unverified.stream().filter(event -> event.sessionId().equals(sessionId)).toList();
    }
  }

  @BeforeEach
  void theOtherModulesAnswer() {
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
    when(identity.requireUser(any()))
        .thenAnswer(
            call ->
                new UserView(
                    call.getArgument(0), UPC, UserRole.STUDENT, "u202400001@upc.edu.pe",
                    "U202400001", "Ana Torres", "Software Engineering", "2026-2", null));
  }

  /**
   * A session ten minutes into its hour, joined by both, its codes sent, and the student charged
   * one credit for it as booking would.
   *
   * @return the session; its booking is {@link #bookingOf}
   */
  private UUID aSessionUnderway() throws Exception {
    Instant start = now.minus(Duration.ofMinutes(10));
    UUID bookingId = UUID.randomUUID();
    TenantContext.runAs(
        UPC,
        () -> {
          wallet.grant(
              student, Credits.of(3), CreditType.SEED, now.plus(Duration.ofDays(30)),
              UUID.randomUUID());
          wallet.charge(student, Credits.of(1), bookingId);
          scheduleSession.forBooking(
              bookingId, student, tutor, start, start.plus(Duration.ofHours(1)));
        });
    UUID session =
        jdbc.queryForObject(
            "select id from sessions.sessions where tenant_id = ? and booking_id = ?",
            UUID.class,
            UPC,
            bookingId);
    join(session, student).andExpect(status().isOk());
    join(session, tutor).andExpect(status().isOk());
    TenantContext.runAs(UPC, () -> issuePresenceCodes.issueFor(session));
    return session;
  }

  private UUID bookingOf(UUID sessionId) {
    return jdbc.queryForObject(
        "select booking_id from sessions.sessions where id = ?", UUID.class, sessionId);
  }

  private void provePresence(UUID sessionId, UUID participant) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/presence", sessionId)
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", participant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\"}".formatted(outcomes.codeOf(sessionId, participant))))
        .andExpect(status().isOk());
  }

  /** Moves the session back so its booked hour ended that long ago. */
  private void endedAgo(UUID sessionId, Duration ago) {
    Instant end = now.minus(ago);
    jdbc.update(
        "update sessions.sessions set scheduled_start = ?, scheduled_end = ? where id = ?",
        Timestamp.from(end.minus(Duration.ofHours(1))),
        Timestamp.from(end),
        sessionId);
  }

  private ResultActions join(UUID sessionId, UUID asUser) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/{id}/join", sessionId)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", asUser));
  }

  private ResultActions end(UUID sessionId, UUID asUser) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/{id}/end", sessionId)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", asUser));
  }

  private String statusOf(UUID sessionId) {
    return jdbc.queryForObject(
        "select status from sessions.sessions where id = ?", String.class, sessionId);
  }

  private Object endConfirmedAt(UUID sessionId, UUID participant) {
    return jdbc.queryForObject(
        "select end_confirmed_at from sessions.participations where session_id = ? and user_id = ?",
        Object.class,
        sessionId,
        participant);
  }

  private Credits balanceOf(UUID userId) {
    Credits[] available = new Credits[1];
    TenantContext.runAs(UPC, () -> available[0] = wallet.balanceOf(userId).available());
    return available[0];
  }

  private Credits earnedTotalOf(UUID userId) {
    Credits[] earned = new Credits[1];
    TenantContext.runAs(UPC, () -> earned[0] = wallet.earnedTotal(userId));
    return earned[0];
  }

  private List<UUID> dueToClose() {
    List<UUID> due = new ArrayList<>();
    TenantContext.runAs(UPC, () -> due.addAll(closeOverdueSessions.dueNow()));
    return due;
  }

  private boolean closeFor(UUID sessionId) {
    boolean[] closed = new boolean[1];
    TenantContext.runAs(UPC, () -> closed[0] = closeOverdueSessions.closeFor(sessionId));
    return closed[0];
  }

  @Test
  @DisplayName("Both confirm the end and the tutor earns the hour")
  void bothConfirmAndTheTutorEarns() throws Exception {

    UUID session = aSessionUnderway();
    provePresence(session, student);
    provePresence(session, tutor);

    end(session, tutor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.endConfirmedAt").exists())
        .andExpect(jsonPath("$.endedAt").doesNotExist())
        .andExpect(
            jsonPath("$.closesAt")
                .value(now.plus(Duration.ofMinutes(65)).toString()));
    assertThat(outcomes.completedOf(session)).isEmpty();

    end(session, student)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.endedAt").exists());

    assertThat(statusOf(session)).isEqualTo("COMPLETED");
    List<SessionCompleted> completed = outcomes.completedOf(session);
    assertThat(completed).hasSize(1);
    assertThat(completed.getFirst().tutorId()).isEqualTo(tutor);
    assertThat(completed.getFirst().bookingId()).isEqualTo(bookingOf(session));
    assertThat(completed.getFirst().catalogItemId()).isEqualTo(course);
    assertThat(completed.getFirst().creditsEarned()).isEqualTo(Credits.of(1));
    // Earned credits: they never expire and count towards recognition.
    assertThat(earnedTotalOf(tutor)).isEqualTo(Credits.of(1));
    assertThat(balanceOf(student)).as("the student paid the hour").isEqualTo(Credits.of(2));

    mockMvc
        .perform(
            get("/api/v1/sessions/{id}", session)
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", tutor))
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.endedAt").exists())
        .andExpect(jsonPath("$.endConfirmedAt").exists());
  }

  @Test
  @DisplayName("Confirming the end again changes nothing")
  void confirmingAgainChangesNothing() throws Exception {

    UUID session = aSessionUnderway();

    end(session, tutor).andExpect(status().isOk());
    Object first = endConfirmedAt(session, tutor);
    end(session, tutor).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

    assertThat(endConfirmedAt(session, tutor)).isEqualTo(first);
    assertThat(statusOf(session)).isEqualTo("IN_PROGRESS");
  }

  @Test
  @DisplayName("With one confirmation, the session closes on its own fifteen minutes after the hour")
  void oneConfirmationClosesOnItsOwn() throws Exception {

    UUID session = aSessionUnderway();
    provePresence(session, student);
    provePresence(session, tutor);
    end(session, tutor).andExpect(status().isOk());

    endedAgo(session, Duration.ofMinutes(10));
    assertThat(dueToClose()).doesNotContain(session);
    assertThat(closeFor(session)).isFalse();

    endedAgo(session, Duration.ofMinutes(15));
    assertThat(dueToClose()).contains(session);
    assertThat(closeFor(session)).isTrue();

    assertThat(statusOf(session)).isEqualTo("COMPLETED");
    assertThat(outcomes.completedOf(session)).hasSize(1);
    assertThat(earnedTotalOf(tutor)).isEqualTo(Credits.of(1));
    // Who confirmed stays on record.
    assertThat(endConfirmedAt(session, tutor)).isNotNull();
    assertThat(endConfirmedAt(session, student)).isNull();

    assertThat(closeFor(session)).as("closed once").isFalse();
    assertThat(dueToClose()).doesNotContain(session);
  }

  @Test
  @DisplayName("Without both presence confirmations the session is unverified and the student refunded")
  void withoutPresenceItIsUnverified() throws Exception {

    UUID session = aSessionUnderway();
    provePresence(session, student);

    end(session, student).andExpect(status().isOk());
    end(session, tutor).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNVERIFIED"));

    assertThat(statusOf(session)).isEqualTo("UNVERIFIED");
    assertThat(outcomes.completedOf(session)).isEmpty();
    List<SessionUnverified> unverified = outcomes.unverifiedOf(session);
    assertThat(unverified).hasSize(1);
    assertThat(unverified.getFirst().unconfirmedUserIds()).containsExactly(tutor);
    assertThat(unverified.getFirst().bookingId()).isEqualTo(bookingOf(session));
    assertThat(earnedTotalOf(tutor)).isEqualTo(Credits.ZERO);
    assertThat(balanceOf(student)).as("refunded").isEqualTo(Credits.of(3));
  }

  @Test
  @DisplayName("A session left open by both closes on its own, unverified if nobody proved presence")
  void leftOpenByBoth() throws Exception {

    UUID session = aSessionUnderway();
    endedAgo(session, Duration.ofMinutes(20));

    assertThat(closeFor(session)).isTrue();

    assertThat(statusOf(session)).isEqualTo("UNVERIFIED");
    assertThat(outcomes.unverifiedOf(session).getFirst().unconfirmedUserIds())
        .containsExactlyInAnyOrder(student, tutor);
    assertThat(endConfirmedAt(session, student)).isNull();
    assertThat(endConfirmedAt(session, tutor)).isNull();
  }

  @Test
  @DisplayName("Both confirming at the same moment close the session once")
  void bothAtOnceCloseItOnce() throws Exception {

    UUID session = aSessionUnderway();
    provePresence(session, student);
    provePresence(session, tutor);

    CyclicBarrier bothReady = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> answers = new ArrayList<>();
      for (UUID participant : List.of(student, tutor)) {
        answers.add(
            pool.submit(
                () -> {
                  bothReady.await();
                  return end(session, participant).andReturn().getResponse().getStatus();
                }));
      }
      for (Future<Integer> answer : answers) {
        assertThat(answer.get()).isEqualTo(200);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(statusOf(session)).isEqualTo("COMPLETED");
    assertThat(outcomes.completedOf(session)).hasSize(1);
    assertThat(earnedTotalOf(tutor)).isEqualTo(Credits.of(1));
  }

  @Test
  @DisplayName("Only a participant who joined can end a session in progress")
  void onlyAParticipantInTheRoomCanEnd() throws Exception {

    UUID session = aSessionUnderway();
    end(session, UUID.randomUUID()).andExpect(status().isForbidden());

    Instant start = now.minus(Duration.ofMinutes(10));
    UUID bookingId = UUID.randomUUID();
    TenantContext.runAs(
        UPC,
        () ->
            scheduleSession.forBooking(
                bookingId, student, tutor, start, start.plus(Duration.ofHours(1))));
    UUID onlyTheStudentCame =
        jdbc.queryForObject(
            "select id from sessions.sessions where booking_id = ?", UUID.class, bookingId);
    end(onlyTheStudentCame, student)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("scheduled")));
    join(onlyTheStudentCame, student).andExpect(status().isOk());
    end(onlyTheStudentCame, tutor)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Join the session before ending it"));

    end(session, student).andExpect(status().isOk());
    end(session, tutor).andExpect(status().isOk());
    end(session, tutor)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("only a session in progress")));
    assertThat(outcomes.unverifiedOf(session)).hasSize(1);
  }
}
