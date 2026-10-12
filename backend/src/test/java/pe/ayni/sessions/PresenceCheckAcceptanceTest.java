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
import pe.ayni.sessions.application.IssuePresenceCodesUseCase;
import pe.ayni.sessions.application.ScheduleSessionUseCase;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US54 over HTTP against a real PostgreSQL, one test per scenario of {@code
 * features/US54-presence-code.feature}.
 *
 * <p>Sessions are scheduled with the use case booking's event calls, and joined over HTTP. The
 * codes are issued by calling the use case the job calls, instead of waiting a minute for the job,
 * and read from {@link PresenceCodeIssued}, the only place the code travels in clear besides the
 * email. The email itself is covered by notifications' own acceptance test; here identity only
 * answers who the participants are.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PresenceCheckAcceptanceTest.IssuedCodes.class)
class PresenceCheckAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  private final UUID student = UUID.randomUUID();
  private final UUID tutor = UUID.randomUUID();
  private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ScheduleSessionUseCase scheduleSession;
  @Autowired private IssuePresenceCodesUseCase issuePresenceCodes;
  @Autowired private IssuedCodes issued;
  @MockitoBean private BookingApi booking;
  @MockitoBean private IdentityApi identity;

  /** The codes in clear, per session and participant, as notifications receives them. */
  @TestConfiguration(proxyBeanMethods = false)
  static class IssuedCodes {

    private final Map<UUID, Map<UUID, PresenceCodeIssued>> bySession = new ConcurrentHashMap<>();

    @EventListener
    void on(PresenceCodeIssued event) {
      bySession
          .computeIfAbsent(event.sessionId(), id -> new ConcurrentHashMap<>())
          .put(event.userId(), event);
    }

    Map<UUID, PresenceCodeIssued> of(UUID sessionId) {
      return bySession.getOrDefault(sessionId, Map.of());
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
                    UUID.randomUUID(),
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
                    "u202400001", "Ana Quispe", "Software Engineering", "7", null));
  }

  /** A session scheduled to start that long ago (or from now, if positive), lasting one hour. */
  private UUID aSessionStarting(Duration fromNow) {
    Instant start = now.plus(fromNow);
    UUID bookingId = UUID.randomUUID();
    TenantContext.runAs(
        UPC,
        () ->
            scheduleSession.forBooking(
                bookingId, student, tutor, start, start.plus(Duration.ofHours(1))));
    return jdbc.queryForObject(
        "select id from sessions.sessions where tenant_id = ? and booking_id = ?",
        UUID.class,
        UPC,
        bookingId);
  }

  /** A session ten minutes into its hour, joined by both participants: its codes are due. */
  private UUID aSessionTenMinutesIn() throws Exception {
    UUID session = aSessionStarting(Duration.ofMinutes(-10));
    join(session, student).andExpect(status().isOk());
    join(session, tutor).andExpect(status().isOk());
    return session;
  }

  private List<UUID> dueNow() {
    List<UUID> due = new ArrayList<>();
    TenantContext.runAs(UPC, () -> due.addAll(issuePresenceCodes.dueNow()));
    return due;
  }

  private boolean issueFor(UUID sessionId) {
    boolean[] issuedNow = new boolean[1];
    TenantContext.runAs(UPC, () -> issuedNow[0] = issuePresenceCodes.issueFor(sessionId));
    return issuedNow[0];
  }

  private String codeOf(UUID sessionId, UUID participant) {
    return issued.of(sessionId).get(participant).code();
  }

  private ResultActions join(UUID sessionId, UUID asUser) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/{id}/join", sessionId)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", asUser));
  }

  private ResultActions confirm(UUID sessionId, UUID asUser, String code) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/{id}/presence", sessionId)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", asUser)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\": \"%s\"}".formatted(code)));
  }

  private ResultActions read(UUID sessionId, UUID asUser) throws Exception {
    return mockMvc.perform(
        get("/api/v1/sessions/{id}", sessionId).header("X-Tenant-Id", UPC).header("X-User-Id", asUser));
  }

  /** A six digit code that is not this one. */
  private static String anotherCode(String code) {
    return code.equals("000000") ? "000001" : "000000";
  }

  private Map<String, Object> checkOf(UUID sessionId, UUID participant) {
    return jdbc.queryForMap(
        """
        select code_hash, expires_at, issued_at, confirmed_at, attempts
        from sessions.presence_checks
        where tenant_id = ? and session_id = ? and user_id = ?
        """,
        UPC,
        sessionId,
        participant);
  }

  @Test
  @DisplayName("Five minutes into the session each participant gets a code")
  void eachParticipantGetsACode() throws Exception {

    UUID session = aSessionTenMinutesIn();

    assertThat(dueNow()).contains(session);
    assertThat(issueFor(session)).isTrue();

    Map<UUID, PresenceCodeIssued> codes = issued.of(session);
    assertThat(codes).containsOnlyKeys(student, tutor);
    for (PresenceCodeIssued code : codes.values()) {
      assertThat(code.tenantId()).isEqualTo(UPC);
      assertThat(code.code()).matches("\\d{6}");
      assertThat(Duration.between(code.occurredOn(), code.expiresAt()))
          .isEqualTo(Duration.ofMinutes(15));
      // Only the hash is kept.
      assertThat((String) checkOf(session, code.userId()).get("code_hash"))
          .hasSize(64)
          .doesNotContain(code.code());
    }

    // Issued once: the next sweep finds nothing to do.
    assertThat(dueNow()).doesNotContain(session);
    assertThat(issueFor(session)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sessions.presence_checks where session_id = ?",
                Integer.class,
                session))
        .isEqualTo(2);

    read(session, student)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.presence.attemptsLeft").value(5))
        .andExpect(jsonPath("$.presence.confirmedAt").doesNotExist())
        .andExpect(jsonPath("$.presence.code").doesNotExist());
  }

  @Test
  @DisplayName("No code before the fifth minute, for a session nobody joined, or after the end")
  void noCodeOutsideTheSession() throws Exception {

    UUID justStarted = aSessionStarting(Duration.ofMinutes(-2));
    join(justStarted, student).andExpect(status().isOk());

    UUID nobodyCame = aSessionStarting(Duration.ofMinutes(-10));

    UUID over = aSessionStarting(Duration.ofMinutes(-10));
    join(over, student).andExpect(status().isOk());
    jdbc.update(
        "update sessions.sessions set scheduled_end = ? where id = ?",
        Timestamp.from(now.minusSeconds(1)),
        over);

    assertThat(dueNow()).doesNotContain(justStarted, nobodyCame, over);
    assertThat(issueFor(justStarted)).isFalse();
    assertThat(issueFor(nobodyCame)).isFalse();
    assertThat(issueFor(over)).isFalse();
    assertThat(issued.of(justStarted)).isEmpty();

    read(justStarted, student)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.presenceCheckAt")
                .value(now.minus(Duration.ofMinutes(2)).plus(Duration.ofMinutes(5)).toString()))
        .andExpect(jsonPath("$.presence").doesNotExist());
  }

  @Test
  @DisplayName("Typing the code confirms presence, and typing it again changes nothing")
  void theCodeConfirmsPresence() throws Exception {

    UUID session = aSessionTenMinutesIn();
    issueFor(session);

    confirm(session, student, codeOf(session, student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value(session.toString()))
        .andExpect(jsonPath("$.confirmedAt").exists());
    Object confirmedAt = checkOf(session, student).get("confirmed_at");
    assertThat(confirmedAt).isNotNull();

    confirm(session, student, codeOf(session, student)).andExpect(status().isOk());
    assertThat(checkOf(session, student).get("confirmed_at")).isEqualTo(confirmedAt);
    assertThat(checkOf(session, tutor).get("confirmed_at"))
        .as("each participant confirms their own")
        .isNull();

    read(session, student)
        .andExpect(jsonPath("$.presence.confirmedAt").exists())
        .andExpect(jsonPath("$.presence.attemptsLeft").value(5));
  }

  @Test
  @DisplayName("A participant cannot confirm with the other participant's code")
  void theOtherParticipantsCodeIsWrong() throws Exception {

    UUID session = aSessionTenMinutesIn();
    issueFor(session);
    String studentsCode = codeOf(session, student);
    if (studentsCode.equals(codeOf(session, tutor))) {
      return; // One chance in a million: the same six digits for both.
    }

    confirm(session, tutor, studentsCode).andExpect(status().isUnprocessableEntity());
    assertThat(checkOf(session, tutor).get("confirmed_at")).isNull();
  }

  @Test
  @DisplayName("A wrong code spends one attempt, and the attempt is kept")
  void aWrongCodeSpendsAnAttempt() throws Exception {

    UUID session = aSessionTenMinutesIn();
    issueFor(session);

    confirm(session, student, anotherCode(codeOf(session, student)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.message").value("That is not the code you were sent. Attempts left: 4"))
        .andExpect(jsonPath("$.path").value("/api/v1/sessions/" + session + "/presence"));

    // Kept although the answer was a refusal.
    assertThat(((Number) checkOf(session, student).get("attempts")).intValue()).isEqualTo(1);
    read(session, student).andExpect(jsonPath("$.presence.attemptsLeft").value(4));

    confirm(session, student, codeOf(session, student)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("After five wrong codes the code can no longer be used")
  void fiveWrongCodesKillIt() throws Exception {

    UUID session = aSessionTenMinutesIn();
    issueFor(session);
    String wrong = anotherCode(codeOf(session, student));

    for (int attempt = 1; attempt <= 4; attempt++) {
      confirm(session, student, wrong).andExpect(status().isUnprocessableEntity());
    }
    confirm(session, student, wrong)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message", containsString("can no longer be used")));

    confirm(session, student, codeOf(session, student))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("can no longer be used")));
    assertThat(((Number) checkOf(session, student).get("attempts")).intValue()).isEqualTo(5);
    assertThat(checkOf(session, student).get("confirmed_at")).isNull();
  }

  @Test
  @DisplayName("Wrong codes sent at once still stop at five")
  void wrongCodesAtOnceStopAtFive() throws Exception {

    UUID session = aSessionTenMinutesIn();
    issueFor(session);
    String wrong = anotherCode(codeOf(session, student));

    int guesses = 8;
    CyclicBarrier allReady = new CyclicBarrier(guesses);
    ExecutorService pool = Executors.newFixedThreadPool(guesses);
    List<Integer> answers = new ArrayList<>();
    try {
      List<Future<Integer>> pending = new ArrayList<>();
      for (int i = 0; i < guesses; i++) {
        pending.add(
            pool.submit(
                () -> {
                  allReady.await();
                  return confirm(session, student, wrong).andReturn().getResponse().getStatus();
                }));
      }
      for (Future<Integer> answer : pending) {
        answers.add(answer.get());
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(answers).filteredOn(status -> status == 422).hasSize(5);
    assertThat(answers).filteredOn(status -> status == 409).hasSize(3);
    assertThat(((Number) checkOf(session, student).get("attempts")).intValue()).isEqualTo(5);
  }

  @Test
  @DisplayName("An expired code is refused")
  void anExpiredCodeIsRefused() throws Exception {

    UUID session = aSessionTenMinutesIn();
    issueFor(session);
    jdbc.update(
        "update sessions.presence_checks set issued_at = ?, expires_at = ? where session_id = ?",
        Timestamp.from(now.minus(Duration.ofMinutes(20))),
        Timestamp.from(now.minus(Duration.ofMinutes(5))),
        session);

    confirm(session, student, codeOf(session, student))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("expired")));
    assertThat(((Number) checkOf(session, student).get("attempts")).intValue()).isZero();
  }

  @Test
  @DisplayName("Presence cannot be confirmed before the code, without joining, or by somebody else")
  void presenceNeedsACodeAndAParticipantInTheRoom() throws Exception {

    UUID notYet = aSessionStarting(Duration.ofMinutes(-2));
    join(notYet, student).andExpect(status().isOk());
    confirm(notYet, student, "123456")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("has not been sent yet")));

    UUID tutorAway = aSessionStarting(Duration.ofMinutes(-10));
    join(tutorAway, student).andExpect(status().isOk());
    issueFor(tutorAway);
    assertThat(issued.of(tutorAway)).as("the absent tutor gets a code too").containsKey(tutor);
    confirm(tutorAway, tutor, codeOf(tutorAway, tutor))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Join the session before confirming your presence"));

    confirm(tutorAway, UUID.randomUUID(), "123456").andExpect(status().isForbidden());

    UUID scheduled = aSessionStarting(Duration.ofHours(2));
    confirm(scheduled, student, "123456")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("scheduled")));

    confirm(tutorAway, student, "12ab56")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("code must be six digits"));
    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/presence", tutorAway)
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("code is required"));
    assertThat(((Number) checkOf(tutorAway, student).get("attempts")).intValue())
        .as("a malformed code is not an attempt")
        .isZero();
  }
}
