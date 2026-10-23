package pe.ayni.config.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.AccessPurpose;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;
import pe.ayni.wallet.application.ExpireCredits;

/**
 * The clock of the demo profile over HTTP, against a real PostgreSQL with real session tokens. One
 * test per scenario of {@code features/demo-profile.feature} that names this class, with the same
 * display name.
 *
 * <p>The jobs are pushed out of reach, as in every other test, so that none of them runs while a
 * test moves the clock: the scenario about credits calls the use case the expiry job calls.
 */
@SpringBootTest(
    properties = {
      "ayni.test.identity-headers=false",
      "ayni.wallet.expiry-cron=-",
      "ayni.booking.generation-cron=-",
      "ayni.booking.hold-release-delay=PT24H",
      "ayni.sessions.presence-check-delay=PT24H",
      "ayni.sessions.closing-delay=PT24H",
      "ayni.sessions.no-show-delay=PT24H",
      "ayni.audit.analysis-delay=PT24H",
      "ayni.payments.reconciliation-delay=PT24H"
    })
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoClockEndpointsTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = DemoTestDatabase.INSTANCE;

  private static final String CLOCK = "/api/v1/admin/demo/clock";

  @Autowired private MockMvc mockMvc;
  @Autowired private Clock clock;
  @Autowired private AdjustableClock adjustableClock;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private PlatformAdminRepository admins;
  @Autowired private AccessLinkRepository accessLinks;
  @Autowired private AccessTokenGenerator tokens;
  @Autowired private WalletApi wallet;
  @Autowired private ExpireCredits expireCredits;

  private SignedInPeople people;
  private String administrator;

  @BeforeEach
  void anAdministratorSignedIn() {
    adjustableClock.reset();
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    administrator = DemoAdministrators.aSignedInAdministrator(admins, userSessions, tokens);
  }

  /** The clock is shared by every test of the context: each one leaves it at the real time. */
  @AfterEach
  void backToTheRealTime() {
    adjustableClock.reset();
  }

  private static MockHttpServletRequestBuilder bearer(
      MockHttpServletRequestBuilder request, String token) {
    return request.header("Authorization", "Bearer " + token);
  }

  private ResultActions advance(String token, String body) throws Exception {
    return mockMvc.perform(
        bearer(post(CLOCK + "/advance"), token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions setTo(String token, String body) throws Exception {
    return mockMvc.perform(
        bearer(put(CLOCK), token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static Duration offsetOf(ResultActions answer) throws Exception {
    return Duration.parse(
        JsonPath.read(answer.andReturn().getResponse().getContentAsString(), "$.offset"));
  }

  private static Instant instantOf(ResultActions answer, String field) throws Exception {
    return Instant.parse(
        JsonPath.read(answer.andReturn().getResponse().getContentAsString(), "$." + field));
  }

  @Test
  @DisplayName("The administrator reads the clock")
  void theAdministratorReadsTheClock() throws Exception {
    Instant before = Instant.now();

    ResultActions answer =
        mockMvc
            .perform(bearer(get(CLOCK), administrator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.offset").value("PT0S"));

    Instant now = instantOf(answer, "now");
    assertThat(now).isEqualTo(instantOf(answer, "realNow"));
    assertThat(now).isBetween(before, Instant.now());
  }

  @Test
  @DisplayName("The administrator moves the clock forward and back")
  void theAdministratorMovesTheClockForwardAndBack() throws Exception {
    ResultActions ahead =
        advance(administrator, "{\"by\": \"PT2H\"}").andExpect(status().isOk());
    assertThat(offsetOf(ahead)).isEqualTo(Duration.ofHours(2));
    assertThat(Duration.between(instantOf(ahead, "realNow"), instantOf(ahead, "now")))
        .isEqualTo(Duration.ofHours(2));

    ResultActions back =
        advance(administrator, "{\"by\": \"-PT30M\"}").andExpect(status().isOk());
    assertThat(offsetOf(back)).isEqualTo(Duration.ofMinutes(90));

    // Every rule reads the moved time: the Clock the modules inject is this one.
    assertThat(clock).isSameAs(adjustableClock);
    assertThat(Duration.between(Instant.now(), clock.instant()))
        .isBetween(Duration.ofMinutes(89), Duration.ofMinutes(91));
  }

  @Test
  @DisplayName("The administrator sets the clock at an instant")
  void theAdministratorSetsTheClockAtAnInstant() throws Exception {
    Instant at = Instant.now().plus(Duration.ofDays(40)).truncatedTo(ChronoUnit.MINUTES);

    ResultActions answer =
        setTo(administrator, "{\"at\": \"" + at + "\"}").andExpect(status().isOk());

    assertThat(instantOf(answer, "now")).isBetween(at, at.plusSeconds(5));
    assertThat(clock.instant()).isBetween(at, at.plusSeconds(5));
  }

  @Test
  @DisplayName("The administrator takes the clock back to the real time")
  void theAdministratorTakesTheClockBackToTheRealTime() throws Exception {
    advance(administrator, "{\"by\": \"P31D\"}").andExpect(status().isOk());

    mockMvc
        .perform(bearer(delete(CLOCK), administrator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offset").value("PT0S"));

    assertThat(adjustableClock.isMoved()).isFalse();
    assertThat(Duration.between(Instant.now(), clock.instant()).abs())
        .isLessThan(Duration.ofSeconds(1));
  }

  @Test
  @DisplayName("A value that is not a duration or an instant is refused")
  void aValueThatIsNotADurationOrAnInstantIsRefused() throws Exception {
    advance(administrator, "{\"by\": \"two hours\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.path").value(CLOCK + "/advance"));
    advance(administrator, "{}").andExpect(status().isBadRequest());
    advance(administrator, "{\"by\": \"P1W\"}").andExpect(status().isBadRequest());
    advance(administrator, "{\"by\": \"P4000D\"}").andExpect(status().isBadRequest());
    advance(administrator, "not json").andExpect(status().isBadRequest());
    setTo(administrator, "{\"at\": \"tomorrow\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.path").value(CLOCK));
    setTo(administrator, "{\"at\": \"2026-10-22\"}").andExpect(status().isBadRequest());
    setTo(administrator, "{}").andExpect(status().isBadRequest());
    setTo(administrator, "{\"at\": \"2999-01-01T00:00:00Z\"}").andExpect(status().isBadRequest());

    assertThat(adjustableClock.isMoved()).isFalse();
  }

  @Test
  @DisplayName("Only the platform administrator moves the clock")
  void onlyThePlatformAdministratorMovesTheClock() throws Exception {
    Person student = people.aStudent(people.aUniversity("K"));

    mockMvc.perform(bearer(get(CLOCK), student.token())).andExpect(status().isForbidden());
    advance(student.token(), "{\"by\": \"PT2H\"}").andExpect(status().isForbidden());
    setTo(student.token(), "{\"at\": \"" + Instant.now().plus(Duration.ofDays(1)) + "\"}")
        .andExpect(status().isForbidden());
    mockMvc.perform(bearer(delete(CLOCK), student.token())).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post(CLOCK + "/advance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"by\": \"PT2H\"}"))
        .andExpect(status().isUnauthorized());

    assertThat(adjustableClock.isMoved()).isFalse();
  }

  @Test
  @DisplayName("Credits expire when the clock passes their expiry")
  void creditsExpireWhenTheClockPassesTheirExpiry() throws Exception {
    String university = people.aUniversity("K");
    Person student = people.aStudent(university);
    TenantContext.runAs(
        university,
        () ->
            wallet.grant(
                student.id(),
                Credits.of(3),
                CreditType.SEED,
                clock.instant().plus(Duration.ofDays(1)),
                UUID.randomUUID()));

    // The expiry job, run before the clock moves: nothing is due yet.
    assertThat(TenantContext.callAs(university, expireCredits::forCurrentUniversity))
        .isEqualTo(Credits.ZERO);
    mockMvc
        .perform(bearer(get("/api/v1/wallet"), student.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(3));

    advance(administrator, "{\"by\": \"P2D\"}").andExpect(status().isOk());
    Credits expired = TenantContext.callAs(university, expireCredits::forCurrentUniversity);

    assertThat(expired).isEqualTo(Credits.of(3));
    mockMvc
        .perform(bearer(get("/api/v1/wallet"), student.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(0));
    mockMvc
        .perform(bearer(get("/api/v1/wallet/movements"), student.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].reason").value("EXPIRY"))
        .andExpect(jsonPath("$.items[0].amount").value(3));
  }

  @Test
  @DisplayName("Sessions outlast a jump of the clock and access links do not")
  void sessionsOutlastAJumpOfTheClockAndAccessLinksDoNot() throws Exception {
    String coordinator = "coordinator@upc.edu.pe";

    // A link to sign in as the demo coordinator, as POST /api/v1/access/request stores it.
    String opened = aLoginLinkFor(coordinator);
    String session =
        JsonPath.read(
            mockMvc
                .perform(
                    post("/api/v1/access/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + opened + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COORDINATOR"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.sessionToken");

    String waiting = aLoginLinkFor(coordinator);
    advance(administrator, "{\"by\": \"P30D\"}").andExpect(status().isOk());

    // A month later the session still answers; the link of ten minutes is dead.
    mockMvc.perform(bearer(get("/api/v1/me"), session)).andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/access/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + waiting + "\"}"))
        .andExpect(status().isBadRequest());
  }

  private String aLoginLinkFor(String email) {
    String token = tokens.generate().rawToken();
    Instant now = clock.instant();
    accessLinks.save(
        new AccessLink(
            UUID.randomUUID(),
            "UPC",
            email,
            AccessPurpose.LOGIN,
            tokens.hash(token),
            now.plus(Duration.ofMinutes(10)),
            null,
            now));
    return token;
  }
}
