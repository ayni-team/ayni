package pe.ayni.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.payments.application.PaymentProvider;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.infrastructure.PurchaseRepository;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.PurchaseConfirmed;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;

/**
 * US35 over HTTP against PostgreSQL. Each scenario maps to the correspondingly named test method in
 * {@code features/US35-buy-credits.feature}; wallet is observed only through its public API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class PaymentsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private WalletApi wallet;
  @Autowired private PurchaseRepository purchases;
  @Autowired private Clock clock;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents events;
  @MockitoBean private PaymentProvider provider;

  @BeforeEach
  void paymentProviderConfirmsByDefault() {
    when(provider.charge(any(), any(BigDecimal.class), any()))
        .thenAnswer(
            invocation ->
                new PaymentProvider.PaymentResult(
                    PaymentProvider.PaymentResult.Outcome.CONFIRMED,
                    "test-" + invocation.getArgument(0)));
  }

  @Test
  @DisplayName("A confirmed purchase publishes the event and credits the student immediately")
  void aConfirmedPurchaseCreditsTheStudentImmediately() throws Exception {
    purchase(student, 2, "confirmed-" + UUID.randomUUID())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.credits").value(2))
        .andExpect(jsonPath("$.amount").value(10.00))
        .andExpect(jsonPath("$.currency").value("PEN"))
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.confirmedAt").exists());

    assertThat(availableCredits(student)).isEqualTo(Credits.of(2));
    assertThat(events.stream(PurchaseConfirmed.class).filter(e -> e.studentId().equals(student)))
        .hasSize(1);
  }

  @Test
  @DisplayName("A student who used the monthly limit is told the limit and amount already used")
  void aStudentAtTheLimitIsToldTheLimitAndCreditsUsed() throws Exception {
    purchase(student, 5, "full-" + UUID.randomUUID()).andExpect(status().isCreated());
    assertThat(confirmedCreditsThisMonth(student)).isEqualTo(5);

    purchase(student, 1, "over-" + UUID.randomUUID())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(
            "Monthly purchase limit is 5 credits; 5 already used; maximum available now is 0"));
  }

  @Test
  @DisplayName("A purchase exceeding the remaining allowance reports the maximum amount")
  void aPurchaseAboveTheRemainingLimitReportsTheMaximum() throws Exception {
    purchase(student, 4, "partial-" + UUID.randomUUID()).andExpect(status().isCreated());
    assertThat(confirmedCreditsThisMonth(student)).isEqualTo(4);

    purchase(student, 2, "too-many-" + UUID.randomUUID())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(
            "Monthly purchase limit is 5 credits; 4 already used; maximum available now is 1"));
  }

  @Test
  @DisplayName("A rejected payment grants no credits and leaves the monthly allowance available")
  void aRejectedPaymentDoesNotConsumeCreditsOrTheLimit() throws Exception {
    when(provider.charge(any(), any(BigDecimal.class), any()))
        .thenReturn(
            new PaymentProvider.PaymentResult(
                PaymentProvider.PaymentResult.Outcome.REJECTED, "declined"));

    purchase(student, 5, "declined-" + UUID.randomUUID())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.confirmedAt").doesNotExist());
    assertThat(availableCredits(student)).isEqualTo(Credits.ZERO);
    assertThat(events.stream(PurchaseConfirmed.class).filter(e -> e.studentId().equals(student)))
        .isEmpty();

    when(provider.charge(any(), any(BigDecimal.class), any()))
        .thenReturn(
            new PaymentProvider.PaymentResult(
                PaymentProvider.PaymentResult.Outcome.CONFIRMED, "accepted"));
    purchase(student, 5, "retry-" + UUID.randomUUID())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    assertThat(availableCredits(student)).isEqualTo(Credits.of(5));
  }

  @Test
  @DisplayName("A repeated idempotency key returns the same purchase without crediting twice")
  void aRepeatedIdempotencyKeyDoesNotCreditTwice() throws Exception {
    String key = "repeat-" + UUID.randomUUID();
    purchase(student, 2, key).andExpect(status().isCreated());
    purchase(student, 2, key)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.credits").value(2));

    assertThat(availableCredits(student)).isEqualTo(Credits.of(2));
    assertThat(events.stream(PurchaseConfirmed.class).filter(e -> e.studentId().equals(student)))
        .hasSize(1);
  }

  @Test
  @DisplayName("An idempotency key cannot be reused with a different credit amount")
  void anIdempotencyKeyCannotBeReusedForAnotherAmount() throws Exception {
    String key = "changed-" + UUID.randomUUID();
    purchase(student, 2, key).andExpect(status().isCreated());

    purchase(student, 3, key)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(
            "This idempotency key was already used for a different purchase"));
    assertThat(availableCredits(student)).isEqualTo(Credits.of(2));
  }

  @Test
  @DisplayName("Purchased credits do not contribute to recognition progress")
  void purchasedCreditsDoNotCountTowardsRecognition() throws Exception {
    purchase(student, 3, "recognition-" + UUID.randomUUID()).andExpect(status().isCreated());

    Credits[] earned = new Credits[1];
    TenantContext.runAs(UPC, () -> earned[0] = wallet.earnedTotal(student));
    assertThat(earned[0]).isEqualTo(Credits.ZERO);
  }

  @Test
  @DisplayName("The monthly allowance resets on the first day of the next UTC calendar month")
  void theMonthlyAllowanceResetsAtTheNextCalendarMonth() throws Exception {
    Instant previousMonth =
        YearMonth.from(clock.instant().atZone(ZoneOffset.UTC))
            .minusMonths(1)
            .atDay(15)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC);
    Purchase priorMonthPurchase =
        Purchase.pending(
            UUID.randomUUID(),
            UPC,
            student,
            5,
            new BigDecimal("25.00"),
            "PEN",
            "last-month-" + UUID.randomUUID(),
            previousMonth);
    priorMonthPurchase.confirm("previous-month-payment", previousMonth);
    purchases.save(priorMonthPurchase);

    purchase(student, 5, "new-month-" + UUID.randomUUID())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    assertThat(availableCredits(student)).isEqualTo(Credits.of(5));
  }

  @Test
  @DisplayName("Concurrent purchases cannot spend more than the remaining monthly allowance")
  void concurrentPurchasesCannotExceedTheMonthlyLimit() throws Exception {
    purchase(student, 3, "base-" + UUID.randomUUID()).andExpect(status().isCreated());

    var executor = Executors.newFixedThreadPool(2);
    try {
      Callable<MvcResult> first =
          () -> purchase(student, 2, "race-a-" + UUID.randomUUID()).andReturn();
      Callable<MvcResult> second =
          () -> purchase(student, 2, "race-b-" + UUID.randomUUID()).andReturn();
      var firstResult = executor.submit(first);
      var secondResult = executor.submit(second);
      int firstStatus = firstResult.get(20, TimeUnit.SECONDS).getResponse().getStatus();
      int secondStatus = secondResult.get(20, TimeUnit.SECONDS).getResponse().getStatus();

      assertThat(java.util.List.of(firstStatus, secondStatus))
          .containsExactlyInAnyOrder(201, 409);
      assertThat(availableCredits(student)).isEqualTo(Credits.of(5));
    } finally {
      executor.shutdownNow();
    }
  }

  private org.springframework.test.web.servlet.ResultActions purchase(
      UUID user, int credits, String idempotencyKey) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/payments/purchases")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", user)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credits\":" + credits + "}"));
  }

  private Credits availableCredits(UUID user) {
    pe.ayni.wallet.CreditBalanceView[] balance = new pe.ayni.wallet.CreditBalanceView[1];
    TenantContext.runAs(UPC, () -> balance[0] = wallet.balanceOf(user));
    return balance[0].available();
  }

  private long confirmedCreditsThisMonth(UUID user) {
    YearMonth month = YearMonth.from(clock.instant().atZone(ZoneOffset.UTC));
    return jdbc.queryForObject(
        """
        select coalesce(sum(credits), 0)
        from payments.purchases
        where tenant_id = ? and student_id = ? and status = 'CONFIRMED'
          and confirmed_at >= ? and confirmed_at < ?
        """,
        Long.class,
        UPC,
        user,
        Timestamp.from(month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)),
        Timestamp.from(month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
  }
}
