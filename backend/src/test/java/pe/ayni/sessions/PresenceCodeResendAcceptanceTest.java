package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
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
import pe.ayni.notifications.application.Email;
import pe.ayni.notifications.application.EmailDelivery;
import pe.ayni.sessions.application.IssuePresenceCodesUseCase;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Sending the presence code again (US54-T4) over HTTP against a real PostgreSQL, with real session
 * tokens and a clock the test moves: one test per resend scenario of {@code
 * features/US54-presence-code.feature}, with the same display name.
 *
 * <p>The sessions are written in their table as the point where the codes are due, in progress
 * with both participants in the room, and the codes are issued by calling the use case the job
 * calls. The codes are read from {@link PresenceCodeIssued}, the one place besides the email where
 * they travel in clear; the email itself is recorded instead of sent.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@Import(PresenceCodeResendAcceptanceTest.Config.class)
class PresenceCodeResendAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;
  @Autowired private IssuedCodes issued;
  @Autowired private SentEmails emails;
  @Autowired private IssuePresenceCodesUseCase issuePresenceCodes;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  /** What the scenarios add to the application: a clock to move, and the codes and emails seen. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Config {

    /** Replaces the system clock everywhere, so every module agrees on now. */
    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    @Bean
    IssuedCodes issuedCodes() {
      return new IssuedCodes();
    }

    /** The email goes nowhere; the test reads what would have left. */
    @Bean
    @Primary
    SentEmails sentEmails() {
      return new SentEmails();
    }
  }

  /** The codes in clear, in the order they were published, per session and participant. */
  static class IssuedCodes {

    private final Map<UUID, List<PresenceCodeIssued>> bySession = new ConcurrentHashMap<>();

    @EventListener
    void on(PresenceCodeIssued event) {
      bySession.computeIfAbsent(event.sessionId(), id -> new CopyOnWriteArrayList<>()).add(event);
    }

    List<PresenceCodeIssued> of(UUID sessionId, UUID participant) {
      return bySession.getOrDefault(sessionId, List.of()).stream()
          .filter(event -> event.userId().equals(participant))
          .toList();
    }

    String latestCodeOf(UUID sessionId, UUID participant) {
      List<PresenceCodeIssued> codes = of(sessionId, participant);
      return codes.get(codes.size() - 1).code();
    }
  }

  /** The emails notifications handed over, by whoever carries them. */
  static class SentEmails implements EmailDelivery {

    private final List<Email> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(Email email) {
      sent.add(email);
    }

    List<Email> to(String address) {
      return sent.stream().filter(email -> email.to().equals(address)).toList();
    }
  }

  private String tenant;
  private Person ana;
  private Person bruno;

  @BeforeEach
  void aUniversityWithAnaAndBruno() {
    clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("R");
    ana = people.aStudent(tenant);
    bruno = people.aStudent(tenant);
  }

  /**
   * A session six minutes into its hour, in progress, with the participants in the room unless the
   * student did not come, and its codes issued when asked.
   */
  private UUID aSession(boolean anaJoined, boolean codesIssued) {
    UUID session = UUID.randomUUID();
    Instant start = clock.instant().minus(Duration.ofMinutes(6));
    jdbc.update(
        """
        insert into sessions.sessions
          (id, tenant_id, booking_id, student_id, tutor_id, scheduled_start, scheduled_end,
           room_name, started_at, status)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS')
        """,
        session,
        tenant,
        UUID.randomUUID(),
        ana.id(),
        bruno.id(),
        Timestamp.from(start),
        Timestamp.from(start.plus(Duration.ofHours(1))),
        "room-" + session,
        Timestamp.from(start.plusSeconds(30)));
    if (anaJoined) {
      joined(session, ana, "STUDENT", start);
    }
    joined(session, bruno, "TUTOR", start);
    if (codesIssued) {
      TenantContext.runAs(tenant, () -> issuePresenceCodes.issueFor(session));
    }
    return session;
  }

  private void joined(UUID session, Person who, String role, Instant start) {
    jdbc.update(
        """
        insert into sessions.participations (id, tenant_id, session_id, user_id, role, joined_at)
        values (?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        tenant,
        session,
        who.id(),
        role,
        Timestamp.from(start.plusSeconds(20)));
  }

  private UUID aSessionWithCodes() {
    return aSession(true, true);
  }

  private ResultActions resend(UUID session, Person who) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/{id}/presence/resend", session)
            .header("Authorization", "Bearer " + who.token()));
  }

  private ResultActions confirm(UUID session, Person who, String code) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/{id}/presence", session)
            .header("Authorization", "Bearer " + who.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\": \"%s\"}".formatted(code)));
  }

  private String hashOf(UUID session, Person who) {
    return jdbc.queryForObject(
        "select code_hash from sessions.presence_checks where session_id = ? and user_id = ?",
        String.class,
        session,
        who.id());
  }

  private Instant instantOf(String column, UUID session, Person who) {
    Timestamp value =
        jdbc.queryForObject(
            "select " + column
                + " from sessions.presence_checks where session_id = ? and user_id = ?",
            Timestamp.class,
            session,
            who.id());
    return value == null ? null : value.toInstant();
  }

  private int numberOf(String column, UUID session, Person who) {
    return jdbc.queryForObject(
        "select " + column
            + " from sessions.presence_checks where session_id = ? and user_id = ?",
        Integer.class,
        session,
        who.id());
  }

  @Test
  @DisplayName("Asking for the code again sends a new one and the old one stops working")
  void askingForTheCodeAgainSendsANewOneAndTheOldOneStopsWorking() throws Exception {
    // Given the presence codes were sent and Ana's email did not arrive
    UUID session = aSessionWithCodes();
    String oldCode = issued.latestCodeOf(session, ana.id());
    String oldHash = hashOf(session, ana);
    String brunosHash = hashOf(session, bruno);
    clock.advance(Duration.ofMinutes(2));

    // When Ana asks for her code again
    String answer =
        resend(session, ana)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then she is answered that it is on its way, and the answer does not carry the code
    assertThat(answer).isEmpty();

    // And a new six digit code is sent to her alone, valid for fifteen minutes from now
    List<PresenceCodeIssued> anasCodes = issued.of(session, ana.id());
    assertThat(anasCodes).hasSize(2);
    PresenceCodeIssued resent = anasCodes.get(1);
    assertThat(resent.tenantId()).isEqualTo(tenant);
    assertThat(resent.code()).matches("\\d{6}").isNotEqualTo(oldCode);
    assertThat(resent.occurredOn()).isEqualTo(clock.instant());
    assertThat(resent.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    assertThat(issued.of(session, bruno.id())).as("Bruno's code is not sent again").hasSize(1);
    assertThat(hashOf(session, bruno)).isEqualTo(brunosHash);
    assertThat(instantOf("expires_at", session, ana)).isEqualTo(resent.expiresAt());
    assertThat(instantOf("issued_at", session, ana)).isEqualTo(clock.instant());
    assertThat(hashOf(session, ana)).isNotEqualTo(oldHash).hasSize(64).doesNotContain(resent.code());

    // And the old code is refused as a wrong code
    confirm(session, ana, oldCode)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message", containsString("Attempts left: 4")));
    assertThat(instantOf("confirmed_at", session, ana)).isNull();

    // And the new code confirms her presence
    confirm(session, ana, resent.code()).andExpect(status().isOk());
    assertThat(instantOf("confirmed_at", session, ana)).isEqualTo(clock.instant());
  }

  @Test
  @DisplayName("A code can be sent again three times, at least a minute apart")
  void aCodeCanBeSentAgainThreeTimesAtLeastAMinuteApart() throws Exception {
    UUID session = aSessionWithCodes();
    clock.advance(Duration.ofMinutes(1));
    resend(session, ana).andExpect(status().isAccepted());

    // When Ana asks again right after a resend
    resend(session, ana)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message", containsString("Try again at " + clock.instant().plusSeconds(60))))
        .andExpect(jsonPath("$.path").value("/api/v1/sessions/" + session + "/presence/resend"));
    // Then she is refused until a minute has passed since the last one
    clock.advance(Duration.ofSeconds(59));
    resend(session, ana).andExpect(status().isConflict());
    assertThat(issued.of(session, ana.id())).as("the issue and one resend").hasSize(2);

    // When she asks three times a minute apart
    clock.advance(Duration.ofSeconds(1));
    resend(session, ana).andExpect(status().isAccepted());
    clock.advance(Duration.ofSeconds(60));
    resend(session, ana).andExpect(status().isAccepted());
    assertThat(numberOf("resend_count", session, ana)).isEqualTo(3);

    // Then the fourth time is refused for good
    clock.advance(Duration.ofHours(1));
    resend(session, ana)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("3 times")));
    assertThat(numberOf("resend_count", session, ana)).isEqualTo(3);
    assertThat(issued.of(session, ana.id())).as("the issue and three resends").hasSize(4);

    // The limit is per person: Bruno still has his three.
    resend(session, bruno).andExpect(status().isAccepted());
    assertThat(numberOf("resend_count", session, bruno)).isEqualTo(1);
  }

  @Test
  @DisplayName("A code that cannot be used cannot be sent again")
  void aCodeThatCannotBeUsedCannotBeSentAgain() throws Exception {
    // Then a resend is refused when her presence is already confirmed
    UUID confirmed = aSessionWithCodes();
    confirm(confirmed, ana, issued.latestCodeOf(confirmed, ana.id())).andExpect(status().isOk());
    resend(confirmed, ana)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("already confirmed")));

    // And when she already spent her five attempts, which a new code does not give back
    UUID spent = aSessionWithCodes();
    String wrong = issued.latestCodeOf(spent, ana.id()).equals("000000") ? "000001" : "000000";
    for (int attempt = 1; attempt <= 5; attempt++) {
      confirm(spent, ana, wrong).andExpect(status().isUnprocessableEntity());
    }
    clock.advance(Duration.ofMinutes(2));
    resend(spent, ana)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("can no longer be used")));
    assertThat(numberOf("attempts", spent, ana)).isEqualTo(5);
    assertThat(numberOf("resend_count", spent, ana)).isZero();
    assertThat(issued.of(spent, ana.id())).hasSize(1);

    // And when no code was sent yet
    UUID noCodes = aSession(true, false);
    resend(noCodes, ana)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("has not been sent yet")));
    // or she never joined
    UUID neverJoined = aSession(false, true);
    resend(neverJoined, ana)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("Join the session")));
    // or the session is not in progress
    UUID over = aSessionWithCodes();
    jdbc.update(
        "update sessions.sessions set status = 'UNVERIFIED', ended_at = ? where id = ?",
        Timestamp.from(clock.instant()),
        over);
    resend(over, ana)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("unverified")));
    assertThat(issued.of(over, ana.id())).hasSize(1);
  }

  @Test
  @DisplayName("Only a participant asks for their own code")
  void onlyAParticipantAsksForTheirOwnCode() throws Exception {
    UUID session = aSessionWithCodes();
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    clock.advance(Duration.ofMinutes(2));

    // Then a stranger is refused
    resend(session, people.aStudent(tenant)).andExpect(status().isForbidden());
    // And a person of another university finds no such session
    resend(session, people.aStudent(people.aUniversity("S"))).andExpect(status().isNotFound());
    // Nobody signed in is not heard either
    mockMvc
        .perform(post("/api/v1/sessions/{id}/presence/resend", session))
        .andExpect(status().isUnauthorized());

    assertThat(issued.of(session, ana.id())).hasSize(1);
    assertThat(issued.of(session, bruno.id())).hasSize(1);
    // A participant asks for their own: Ana's request does not touch Bruno's code.
    resend(session, ana).andExpect(status().isAccepted());
    assertThat(numberOf("resend_count", session, bruno)).isZero();
  }

  @Test
  @DisplayName("Two requests to resend at once send one new code")
  void twoRequestsToResendAtOnceSendOneNewCode() throws Exception {
    UUID session = aSessionWithCodes();
    clock.advance(Duration.ofMinutes(2));

    // When Ana asks for her code again twice at the same moment
    int requests = 2;
    CyclicBarrier bothReady = new CyclicBarrier(requests);
    ExecutorService pool = Executors.newFixedThreadPool(requests);
    List<Integer> answers = new ArrayList<>();
    try {
      List<Future<Integer>> pending = new ArrayList<>();
      for (int i = 0; i < requests; i++) {
        pending.add(
            pool.submit(
                () -> {
                  bothReady.await();
                  return resend(session, ana).andReturn().getResponse().getStatus();
                }));
      }
      for (Future<Integer> answer : pending) {
        answers.add(answer.get(30, TimeUnit.SECONDS));
      }
    } finally {
      pool.shutdownNow();
    }

    // Then one is answered and the other refused, and only one new code is sent
    assertThat(answers).containsExactlyInAnyOrder(202, 409);
    assertThat(issued.of(session, ana.id())).as("the issue and one resend").hasSize(2);
    assertThat(numberOf("resend_count", session, ana)).isEqualTo(1);
    // The code that is valid is the one that was published last.
    confirm(session, ana, issued.latestCodeOf(session, ana.id())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("A resent code travels only by email")
  void aResentCodeTravelsOnlyByEmail() throws Exception {
    UUID session = aSessionWithCodes();
    String firstCode = issued.latestCodeOf(session, ana.id());
    clock.advance(Duration.ofMinutes(2));

    // When Ana asks for her code again
    resend(session, ana).andExpect(status().isAccepted());
    String newCode = issued.latestCodeOf(session, ana.id());

    // Then the notice she receives keeps the session and the expiry but never the code
    String anasAddress =
        jdbc.queryForObject("select email from identity.users where id = ?", String.class, ana.id());
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> emails.to(anasAddress).size() == 2);
    List<String> payloads =
        jdbc.queryForList(
            """
            select payload::text from notifications.notifications
            where recipient_id = ? and kind = 'PRESENCE_CODE'
            """,
            String.class,
            ana.id());
    assertThat(payloads).hasSize(2);
    assertThat(payloads)
        .allSatisfy(
            payload ->
                assertThat(payload)
                    .contains(session.toString())
                    .contains("expiresAt")
                    .doesNotContain(firstCode)
                    .doesNotContain(newCode));
    // The email is the one place the new code is.
    assertThat(emails.to(anasAddress).get(1).body()).contains(newCode);

    // And only the hash of the new code is stored
    assertThat(hashOf(session, ana)).hasSize(64).doesNotContain(newCode);
  }
}
