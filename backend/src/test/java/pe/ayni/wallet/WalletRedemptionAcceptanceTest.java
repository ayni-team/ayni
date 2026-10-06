package pe.ayni.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;

@SpringBootTest
@AutoConfigureMockMvc
class WalletRedemptionAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = WalletTestDatabase.INSTANCE;

  private final String tenant = ("R" + UUID.randomUUID().toString().replace("-", "")).substring(0, 30);
  private final UUID student = UUID.randomUUID();
  private final UUID coordinator = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private WalletApi wallet;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;

  @BeforeEach
  void coordinatorIsKnownToIdentity() {
    when(identity.activeTenantCodes()).thenReturn(List.of());
    when(identity.requireUser(student))
        .thenReturn(
            new UserView(
                student,
                tenant,
                UserRole.STUDENT,
                "student@campus.edu",
                "202400001",
                "Student",
                "Engineering",
                "2026-2",
                null));
    when(identity.requireUser(coordinator))
        .thenReturn(
            new UserView(
                coordinator,
                tenant,
                UserRole.COORDINATOR,
                "coordinator@campus.edu",
                null,
                "Campus Coordinator",
                null,
                null,
                null));
  }

  @Test
  @DisplayName("A confirmed campus benefit redemption spends earned credits and returns a receipt")
  void aConfirmedCampusBenefitRedemptionSpendsEarnedCreditsAndReturnsAReceipt()
      throws Exception {
    grant(student, 5, CreditType.EARNED);
    UUID benefitId = createBenefit("Library voucher", 3);
    String key = "benefit-" + UUID.randomUUID();
    UUID confirmationId = requestBenefitConfirmation(benefitId, key);

    String response =
        redeem(benefitId, key, confirmationId, true)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmationRequired").value(false))
            .andExpect(jsonPath("$.kind").value("CAMPUS_BENEFIT_REDEMPTION"))
            .andExpect(jsonPath("$.benefitName").value("Library voucher"))
            .andExpect(jsonPath("$.credits").value(3))
            .andExpect(jsonPath("$.id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String redemptionId = JsonPath.read(response, "$.id");

    assertBalance(2);
    assertEarnedTotal(5);
    mockMvc
        .perform(
            get("/api/v1/wallet/movements")
                .param("reason", "CAMPUS_BENEFIT_REDEMPTION")
                .header("X-Tenant-Id", tenant)
                .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.items[0].amount").value(3))
        .andExpect(jsonPath("$.items[0].referenceId").value(redemptionId));

    redeem(benefitId, key, confirmationId, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(redemptionId));
    assertBalance(2);
    assertThat(countUses("CAMPUS_BENEFIT_REDEMPTION")).isEqualTo(1);
  }

  @Test
  @DisplayName("A confirmed donation reduces earned credits and increases the campus incoming pool")
  void aConfirmedDonationReducesEarnedCreditsAndIncreasesTheIncomingPool() throws Exception {
    grant(student, 4, CreditType.EARNED);
    grant(student, 2, CreditType.PURCHASED);
    String key = "donation-" + UUID.randomUUID();
    UUID confirmationId = requestDonationConfirmation(3, key);

    donate(3, key, confirmationId, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmationRequired").value(false))
        .andExpect(jsonPath("$.kind").value("INCOMING_STUDENT_DONATION"))
        .andExpect(jsonPath("$.credits").value(3))
        .andExpect(jsonPath("$.donationPoolBalance").value(3));

    assertBalance(3);
    assertEarnedTotal(4);
    mockMvc
        .perform(get("/api/v1/wallet/donation-pool").header("X-Tenant-Id", tenant)
            .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.credits").value(3))
        .andExpect(jsonPath("$.contributions").value(1));
    mockMvc
        .perform(get("/api/v1/wallet/donations").header("X-Tenant-Id", tenant)
            .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].credits").value(3))
        .andExpect(jsonPath("$[0].kind").value("INCOMING_STUDENT_DONATION"));
    mockMvc
        .perform(
            get("/api/v1/wallet/movements")
                .param("reason", "INCOMING_STUDENT_DONATION")
                .header("X-Tenant-Id", tenant)
                .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.items[0].creditType").value("EARNED"));
    assertThat(countUses("INCOMING_STUDENT_DONATION")).isEqualTo(1);
  }

  @Test
  @DisplayName("A redemption and donation require a warning round trip before confirmation")
  void aRedemptionAndDonationRequireAWarningRoundTripBeforeConfirmation() throws Exception {
    grant(student, 4, CreditType.EARNED);
    UUID benefitId = createBenefit("Campus meal", 2);
    String benefitKey = "warning-benefit-" + UUID.randomUUID();
    String donationKey = "warning-donation-" + UUID.randomUUID();

    UUID benefitConfirmation = requestBenefitConfirmation(benefitId, benefitKey);
    UUID donationConfirmation = requestDonationConfirmation(1, donationKey);

    assertThat(benefitConfirmation).isNotNull();
    assertThat(donationConfirmation).isNotNull();
    assertBalance(4);
    redeem(benefitId, benefitKey, null, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(
            "Request a confirmation warning before completing this irreversible operation"));
    donate(1, donationKey, null, true).andExpect(status().isConflict());
    assertBalance(4);
    assertThat(countUses("CAMPUS_BENEFIT_REDEMPTION")).isZero();
    assertThat(countUses("INCOMING_STUDENT_DONATION")).isZero();
  }

  @Test
  @DisplayName("A benefit redemption refuses when earned credits are insufficient")
  void aBenefitRedemptionRefusesWhenEarnedCreditsAreInsufficient() throws Exception {
    grant(student, 1, CreditType.EARNED);
    grant(student, 4, CreditType.PURCHASED);
    UUID benefitId = createBenefit("Exam printing", 3);
    String key = "short-" + UUID.randomUUID();
    UUID confirmationId = requestBenefitConfirmation(benefitId, key);

    redeem(benefitId, key, confirmationId, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Insufficient earned credits, missing 2"));

    assertBalance(5);
    assertEarnedTotal(1);
    assertThat(countUses("CAMPUS_BENEFIT_REDEMPTION")).isZero();
  }

  @Test
  @DisplayName("Concurrent confirmation requests with the same key redeem a benefit only once")
  void concurrentBenefitConfirmationsRedeemOnlyOnce() throws Exception {
    grant(student, 4, CreditType.EARNED);
    UUID benefitId = createBenefit("Study room", 3);
    String key = "race-" + UUID.randomUUID();
    UUID confirmationId = requestBenefitConfirmation(benefitId, key);

    var executor = Executors.newFixedThreadPool(2);
    CountDownLatch startTogether = new CountDownLatch(1);
    try {
      Callable<Integer> confirm =
          () -> {
            startTogether.await();
            return redeem(benefitId, key, confirmationId, true)
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var first = executor.submit(confirm);
      var second = executor.submit(confirm);
      startTogether.countDown();

      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsOnly(200);
    } finally {
      executor.shutdownNow();
    }

    assertBalance(1);
    assertThat(countUses("CAMPUS_BENEFIT_REDEMPTION")).isEqualTo(1);
  }

  @Test
  @DisplayName("Only a coordinator can manage benefits and inactive benefits are not offered")
  void onlyCoordinatorsCanManageBenefitsAndInactiveBenefitsAreNotOffered() throws Exception {
    UUID benefitId = createBenefit("Printing", 1);

    mockMvc
        .perform(
            post("/api/v1/wallet/benefits")
                .header("X-Tenant-Id", tenant)
                .header("X-User-Id", student)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Student lounge","description":"Campus lounge access","creditsCost":2,"active":true}
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put("/api/v1/wallet/benefits/{benefitId}", benefitId)
                .header("X-Tenant-Id", tenant)
                .header("X-User-Id", coordinator)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Printing","description":"Campus printing credits","creditsCost":1,"active":false}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            get("/api/v1/wallet/benefits")
                .header("X-Tenant-Id", tenant)
                .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + benefitId + "')]").isEmpty());
  }

  private UUID createBenefit(String name, int credits) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/wallet/benefits")
                    .header("X-Tenant-Id", tenant)
                    .header("X-User-Id", coordinator)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Campus benefit","creditsCost":%d,"active":true}
                        """
                            .formatted(name, credits)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.id"));
  }

  private UUID requestBenefitConfirmation(UUID benefitId, String key) throws Exception {
    String response =
        redeem(benefitId, key, null, false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmationRequired").value(true))
            .andExpect(jsonPath("$.warning").value(
                "This operation is final and cannot be reversed. Confirm to continue."))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.confirmationId"));
  }

  private UUID requestDonationConfirmation(int credits, String key) throws Exception {
    String response =
        donate(credits, key, null, false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmationRequired").value(true))
            .andExpect(jsonPath("$.warning").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.confirmationId"));
  }

  private org.springframework.test.web.servlet.ResultActions redeem(
      UUID benefitId, String key, UUID confirmationId, boolean confirmed) throws Exception {
    String confirmation =
        confirmationId == null ? "" : ",\"confirmationId\":\"" + confirmationId + "\"";
    return mockMvc.perform(
        post("/api/v1/wallet/benefits/{benefitId}/redemptions", benefitId)
            .header("X-Tenant-Id", tenant)
            .header("X-User-Id", student)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"confirmed\":" + confirmed + confirmation + "}"));
  }

  private org.springframework.test.web.servlet.ResultActions donate(
      int credits, String key, UUID confirmationId, boolean confirmed) throws Exception {
    String confirmation =
        confirmationId == null ? "" : ",\"confirmationId\":\"" + confirmationId + "\"";
    return mockMvc.perform(
        post("/api/v1/wallet/donations")
            .header("X-Tenant-Id", tenant)
            .header("X-User-Id", student)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"credits\":" + credits + ",\"confirmed\":" + confirmed + confirmation + "}"));
  }

  private void grant(UUID user, int amount, CreditType type) {
    TenantContext.runAs(
        tenant, () -> wallet.grant(user, Credits.of(amount), type, null, UUID.randomUUID()));
  }

  private void assertBalance(int expected) throws Exception {
    mockMvc
        .perform(
            get("/api/v1/wallet").header("X-Tenant-Id", tenant).header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(expected));
  }

  private void assertEarnedTotal(int expected) throws Exception {
    mockMvc
        .perform(
            get("/api/v1/wallet").header("X-Tenant-Id", tenant).header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.earnedTotal").value(expected));
  }

  private int countUses(String kind) {
    return jdbc.queryForObject(
        "select count(*) from wallet.credit_uses where tenant_id = ? and kind = ?",
        Integer.class,
        tenant,
        kind);
  }

}
