package pe.ayni.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.payments.application.PaymentProvider;
import pe.ayni.payments.application.PaymentProviderUnavailable;
import pe.ayni.payments.application.PendingPurchaseReconciler;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseStatus;
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
@TestPropertySource(properties = {"spring.mail.host=localhost", "spring.mail.port=3025"})
class PaymentsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsTestDatabase.INSTANCE;

  @RegisterExtension
  static final GreenMailExtension INBOX = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final String UPC = "UPC";
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private WalletApi wallet;
  @Autowired private PurchaseRepository purchases;
  @Autowired private Clock clock;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents events;
  @Autowired private PendingPurchaseReconciler reconciler;
  @MockitoBean private PaymentProvider provider;
  @MockitoBean private IdentityApi identity;

  @BeforeEach
  void paymentProviderConfirmsByDefault() {
    when(identity.activeTenantCodes()).thenReturn(List.of());
    when(identity.requireUser(student))
        .thenReturn(
            new UserView(
                student,
                UPC,
                UserRole.STUDENT,
                "student@upc.edu.pe",
                "202400001",
                "Test Student",
                "Engineering",
                "2026-2",
                null));
    when(provider.charge(any(), any(BigDecimal.class), any()))
        .thenAnswer(
            invocation ->
                new PaymentProvider.PaymentResult(
                    PaymentProvider.PaymentResult.Outcome.CONFIRMED,
                    "test-" + invocation.getArgument(0)));
    when(provider.status(any())).thenReturn(Optional.empty());
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
            previousMonth.plus(Duration.ofHours(24)),
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

  @Test
  @DisplayName("A provider confirms an interrupted purchase later and the student is notified")
  void aPurchaseConfirmedOfflineIsCreditedAndTheStudentIsNotified() throws Exception {
    when(provider.charge(any(), any(BigDecimal.class), any()))
        .thenAnswer(
            invocation ->
                new PaymentProvider.PaymentResult(
                    PaymentProvider.PaymentResult.Outcome.PENDING,
                    "provider-" + invocation.getArgument(0)));
    when(identity.activeTenantCodes()).thenReturn(List.of(UPC));
    when(provider.status(any()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new PaymentProvider.PaymentResult(
                        PaymentProvider.PaymentResult.Outcome.CONFIRMED,
                        "provider-" + invocation.getArgument(0))));

    String response =
        purchase(student, 2, "offline-" + UUID.randomUUID())
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.guidance").value(
                "Payment is still being processed. Do not start another purchase; check this purchase's status."))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID purchaseId = UUID.fromString(JsonPath.read(response, "$.id"));

    mockMvc
        .perform(
            get("/api/v1/payments/purchases")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("PENDING"))
        .andExpect(jsonPath("$[0].guidance").value(
            "Payment is still being processed. Do not start another purchase; check this purchase's status."));
    assertThat(availableCredits(student)).isEqualTo(Credits.ZERO);

    reconciler.reconcile();

    mockMvc
        .perform(
            get("/api/v1/payments/purchases")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(purchaseId.toString()))
        .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    assertThat(availableCredits(student)).isEqualTo(Credits.of(2));
    assertThat(events.stream(PurchaseConfirmed.class).filter(e -> e.purchaseId().equals(purchaseId)))
        .hasSize(1);
    assertThat(INBOX.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage email = INBOX.getReceivedMessages()[0];
    assertThat(email.getAllRecipients()[0].toString()).isEqualTo("student@upc.edu.pe");
    assertThat(email.getSubject()).isEqualTo("Tus créditos ya están disponibles en Ayni");
    assertThat((String) email.getContent())
        .contains("acreditamos 2 créditos")
        .contains(purchaseId.toString());

    reconciler.reconcile();
    assertThat(availableCredits(student)).isEqualTo(Credits.of(2));
    assertThat(events.stream(PurchaseConfirmed.class).filter(e -> e.purchaseId().equals(purchaseId)))
        .hasSize(1);
    assertThat(INBOX.getReceivedMessages()).hasSize(1);
  }

  @Test
  @DisplayName("A provider outage leaves the purchase pending and the other APIs available")
  void aProviderOutageLeavesThePurchasePendingWithoutBlockingOtherApis() throws Exception {
    doThrow(new PaymentProviderUnavailable("Provider timeout"))
        .when(provider)
        .charge(any(), any(BigDecimal.class), any());

    purchase(student, 2, "unavailable-" + UUID.randomUUID())
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING"));

    mockMvc
        .perform(
            get("/api/v1/payments/purchases")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("PENDING"));
    mockMvc
        .perform(
            get("/api/v1/wallet")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(0));
  }

  @Test
  @DisplayName("A pending payment past its deadline expires and releases the monthly allowance")
  void anUnconfirmedPurchaseExpiresWithoutConsumingTheMonthlyLimit() throws Exception {
    Instant now = clock.instant();
    Instant createdAt = now.minus(Duration.ofHours(2));
    Purchase pending =
        purchases.save(
            Purchase.pending(
                UUID.randomUUID(),
                UPC,
                student,
                5,
                new BigDecimal("25.00"),
                "PEN",
                "expired-" + UUID.randomUUID(),
                now.minusSeconds(1),
                createdAt));

    when(identity.activeTenantCodes()).thenReturn(List.of(UPC));
    reconciler.reconcile();

    assertThat(purchases.findById(pending.getId()).orElseThrow().getStatus())
        .isEqualTo(PurchaseStatus.EXPIRED);
    purchase(student, 5, "after-expiry-" + UUID.randomUUID())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    assertThat(availableCredits(student)).isEqualTo(Credits.of(5));
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
