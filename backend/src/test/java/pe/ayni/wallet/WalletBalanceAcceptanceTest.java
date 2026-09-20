package pe.ayni.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.application.ExpireCredits;

/**
 * US23, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US23-wallet-balance.feature}, with
 * the same names, so that the acceptance criteria and what actually runs cannot drift apart
 * quietly.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WalletBalanceAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = WalletTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  /** A student of their own, so that one scenario cannot see another one's credits. */
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private WalletApi wallet;
  @Autowired private ExpireCredits expireCredits;

  @Test
  @DisplayName("The balance shows the total and what it is made of")
  void theBalanceShowsTheTotalAndWhatItIsMadeOf() throws Exception {

    Instant inNinetyDays = Instant.now().plus(Duration.ofDays(90));
    Instant inOneHundredAndEightyDays = Instant.now().plus(Duration.ofDays(180));

    grant(6, CreditType.SEED, inNinetyDays);
    grant(4, CreditType.ALLOCATED, inOneHundredAndEightyDays);
    grant(4, CreditType.EARNED, null);

    String balance =
        mockMvc
            .perform(get("/api/v1/wallet").header("X-Tenant-Id", UPC).header("X-User-Id", student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(14))
            // Always in this order, so the wallet reads the same way every time.
            .andExpect(jsonPath("$.byType[0].type").value("SEED"))
            .andExpect(jsonPath("$.byType[0].available").value(6))
            .andExpect(jsonPath("$.byType[1].type").value("ALLOCATED"))
            .andExpect(jsonPath("$.byType[1].available").value(4))
            .andExpect(jsonPath("$.byType[2].type").value("EARNED"))
            .andExpect(jsonPath("$.byType[2].available").value(4))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Instant seedExpiry = instantAt(balance, "$.byType[0].groups[0].expiresAt");
    Instant allocatedExpiry = instantAt(balance, "$.byType[1].groups[0].expiresAt");

    assertThat(seedExpiry).isNotNull();
    assertThat(allocatedExpiry).isAfter(seedExpiry);
  }

  @Test
  @DisplayName(
      "Earned and purchased credits never expire, and only the earned ones count towards"
          + " recognition")
  void earnedAndPurchasedCreditsNeverExpireAndOnlyTheEarnedOnesCountTowardsRecognition()
      throws Exception {

    grant(4, CreditType.EARNED, null);
    grant(2, CreditType.PURCHASED, null);

    mockMvc
        .perform(get("/api/v1/wallet").header("X-Tenant-Id", UPC).header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byType[0].type").value("EARNED"))
        .andExpect(jsonPath("$.byType[0].expires").value(false))
        .andExpect(jsonPath("$.byType[0].groups[0].expiresAt").doesNotExist())
        .andExpect(jsonPath("$.byType[0].countsTowardsRecognition").value(true))
        .andExpect(jsonPath("$.byType[1].type").value("PURCHASED"))
        .andExpect(jsonPath("$.byType[1].expires").value(false))
        .andExpect(jsonPath("$.byType[1].groups[0].expiresAt").doesNotExist())
        // Teaching is what a university recognises. Buying credits is not.
        .andExpect(jsonPath("$.byType[1].countsTowardsRecognition").value(false))
        .andExpect(jsonPath("$.earnedTotal").value(4));
  }

  @Test
  @DisplayName("A student with nothing to spend is told how to obtain credits")
  void aStudentWithNothingToSpendIsToldHowToObtainCredits() throws Exception {

    mockMvc
        .perform(get("/api/v1/wallet").header("X-Tenant-Id", UPC).header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(0))
        .andExpect(jsonPath("$.byType").isEmpty())
        .andExpect(jsonPath("$.guidance").isNotEmpty());
  }

  @Test
  @DisplayName("Credits that expired are no longer available but stay in the history")
  void creditsThatExpiredAreNoLongerAvailableButStayInTheHistory() throws Exception {

    grant(5, CreditType.SEED, Instant.now().minus(Duration.ofDays(1)));
    TenantContext.runAs(UPC, () -> expireCredits.forCurrentUniversity());

    mockMvc
        .perform(get("/api/v1/wallet").header("X-Tenant-Id", UPC).header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(0));

    String movements =
        mockMvc
            .perform(
                get("/api/v1/wallet/movements")
                    .header("X-Tenant-Id", UPC)
                    .header("X-User-Id", student))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Gone from the balance, still in the history: the grant that brought them and the entry
    // that took them away.
    assertThat(amountsOf(movements, "GRANT")).containsExactly(5);
    assertThat(amountsOf(movements, "EXPIRY")).containsExactly(5);
  }

  private void grant(int amount, CreditType type, Instant expiresAt) {
    TenantContext.runAs(
        UPC, () -> wallet.grant(student, Credits.of(amount), type, expiresAt, UUID.randomUUID()));
  }

  private static Instant instantAt(String json, String path) {
    String value = JsonPath.read(json, path);
    return Instant.parse(value);
  }

  private static List<Integer> amountsOf(String json, String reason) {
    return JsonPath.read(json, "$.items[?(@.reason=='" + reason + "')].amount");
  }
}
