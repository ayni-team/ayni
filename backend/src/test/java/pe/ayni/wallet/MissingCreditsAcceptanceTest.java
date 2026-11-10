package pe.ayni.wallet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.LedgerIntegrityAcceptanceTest.MovableClock;

/**
 * W27, the wallet's part, over HTTP against a real PostgreSQL with real session tokens: one test
 * per scenario of {@code features/W27-missing-credits.feature} that the wallet answers, with the
 * same names. Each test has a university of its own, with a coordinator who creates the benefit
 * and a student who redeems or donates.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class MissingCreditsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = WalletTestDatabase.INSTANCE;

  /** Moves the clock the application reads, so a warning can expire without waiting. */
  @TestConfiguration(proxyBeanMethods = false)
  static class MovingTime {

    @Bean
    @Primary
    MovableClock movableClock() {
      return new MovableClock(Instant.now());
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private WalletApi wallet;
  @Autowired private MovableClock clock;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  private String tenant;
  private Person coordinator;
  private Person student;

  @BeforeEach
  void aUniversityWithACoordinatorAndAStudent() {
    clock.set(Instant.now());
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("W");
    coordinator = people.aCoordinator(tenant);
    student = people.aStudent(tenant);
  }

  @Test
  @DisplayName("A redemption without enough earned credits says how many are missing")
  void aRedemptionWithoutEnoughEarnedCreditsSaysHowManyAreMissing() throws Exception {
    grant(1, CreditType.EARNED);
    grant(4, CreditType.PURCHASED);
    UUID benefit = createBenefit(3);
    String key = "short-" + UUID.randomUUID();
    UUID confirmation = warningOf(redeem(benefit, key, null, false));

    redeem(benefit, key, confirmation, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_EARNED_CREDITS"))
        .andExpect(jsonPath("$.missingCredits").value(2))
        .andExpect(jsonPath("$.message").value("Insufficient earned credits, missing 2"));

    as(student, get("/api/v1/wallet"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(5));
  }

  @Test
  @DisplayName("A donation without enough earned credits says how many are missing")
  void aDonationWithoutEnoughEarnedCreditsSaysHowManyAreMissing() throws Exception {
    grant(2, CreditType.EARNED);
    String key = "donation-" + UUID.randomUUID();
    UUID confirmation = warningOf(donate(5, key, null, false));

    donate(5, key, confirmation, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_EARNED_CREDITS"))
        .andExpect(jsonPath("$.missingCredits").value(3));
  }

  @Test
  @DisplayName("An expired confirmation is told apart from missing credits")
  void anExpiredConfirmationIsToldApartFromMissingCredits() throws Exception {
    grant(1, CreditType.EARNED);
    UUID benefit = createBenefit(3);
    String key = "late-" + UUID.randomUUID();
    UUID confirmation = warningOf(redeem(benefit, key, null, false));

    clock.set(clock.instant().plus(Duration.ofMinutes(11)));

    redeem(benefit, key, confirmation, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFIRMATION_EXPIRED"))
        .andExpect(jsonPath("$.missingCredits").doesNotExist());
  }

  @Test
  @DisplayName("Other errors carry neither a code nor missing credits")
  void otherErrorsCarryNeitherACodeNorMissingCredits() throws Exception {
    as(student, get("/api/v1/wallet/movements").param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.code").doesNotExist())
        .andExpect(jsonPath("$.missingCredits").doesNotExist());
  }

  private void grant(int amount, CreditType type) {
    TenantContext.runAs(
        tenant,
        () -> wallet.grant(student.id(), Credits.of(amount), type, null, UUID.randomUUID()));
  }

  private UUID createBenefit(int credits) throws Exception {
    String response =
        as(
                coordinator,
                post("/api/v1/coordinator/benefits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Exam printing","description":"Campus benefit","creditsCost":%d,"active":true}
                        """
                            .formatted(credits)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.id"));
  }

  private static UUID warningOf(ResultActions warning) throws Exception {
    String response =
        warning
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmationRequired").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.confirmationId"));
  }

  private ResultActions redeem(UUID benefit, String key, UUID confirmation, boolean confirmed)
      throws Exception {
    return as(
        student,
        post("/api/v1/wallet/benefits/{benefitId}/redemptions", benefit)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(confirmed, confirmation, "")));
  }

  private ResultActions donate(int credits, String key, UUID confirmation, boolean confirmed)
      throws Exception {
    return as(
        student,
        post("/api/v1/wallet/donations")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(confirmed, confirmation, "\"credits\":" + credits + ",")));
  }

  private static String body(boolean confirmed, UUID confirmation, String prefix) {
    String id = confirmation == null ? "" : ",\"confirmationId\":\"" + confirmation + "\"";
    return "{" + prefix + "\"confirmed\":" + confirmed + id + "}";
  }

  private ResultActions as(Person person, MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request.header("Authorization", "Bearer " + person.token()));
  }
}
