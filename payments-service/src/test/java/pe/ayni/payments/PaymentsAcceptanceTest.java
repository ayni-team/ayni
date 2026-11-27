package pe.ayni.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.ayni.payments.InternalRequests.asMonolith;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.payments.application.PaymentProvider;
import pe.ayni.payments.application.PaymentProviderUnavailable;
import pe.ayni.payments.application.PendingPurchaseReconciler;
import pe.ayni.payments.application.PurchaseRepository;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseStatus;
import pe.ayni.payments.tenancy.TenantContext;

/**
 * US35, US36 and US37 in the payments service, over HTTP against PostgreSQL connected as the role
 * row level security applies to. Each scenario maps to the correspondingly named test method in
 * {@code features/US35-buy-credits.feature}, {@code US36-resolve-pending-purchase.feature} and
 * {@code US37-check-purchase-details.feature}. The wallet is in the monolith: what a confirmed
 * purchase owes it is read from the outbox.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final String USIL = "USIL";
  private final UUID student = UUID.randomUUID();
  private final JdbcTemplate owner = PaymentsTestDatabase.owner();

  @Autowired private MockMvc mockMvc;
  @Autowired private PurchaseRepository purchases;
  @Autowired private Clock clock;
  @Autowired private PendingPurchaseReconciler reconciler;
  @MockitoBean private PaymentProvider provider;

  @BeforeEach
  void paymentProviderConfirmsByDefault() {
    when(provider.charge(any(), any(BigDecimal.class), any()))
        .thenAnswer(
            invocation ->
                new PaymentProvider.PaymentResult(
                    PaymentProvider.PaymentResult.Outcome.CONFIRMED,
                    "test-" + invocation.getArgument(0)));
    when(provider.status(any())).thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("A confirmed purchase credits the student immediately")
  void aConfirmedPurchaseCreditsTheStudentImmediately() throws Exception {
    String response =
        purchase(student, 2, "confirmed-" + UUID.randomUUID())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.credits").value(2))
            .andExpect(jsonPath("$.amount").value(10.00))
            .andExpect(jsonPath("$.currency").value("PEN"))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.confirmedAt").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID purchaseId = UUID.fromString(JsonPath.read(response, "$.id"));

    assertThat(creditsOwedToTheWallet(student)).isEqualTo(2);
    assertThat(confirmedEventsOf(purchaseId)).isEqualTo(1);
    var event =
        owner.queryForMap(
            "select tenant_id, payload->>'tenantId' as payload_tenant, payload->>'studentId' as"
                + " student, (payload->>'credits')::int as credits, payload->>'occurredOn' as"
                + " occurred, sent_at from payments.outbox where aggregate_id = ?",
            purchaseId);
    assertThat(event)
        .containsEntry("tenant_id", UPC)
        .containsEntry("payload_tenant", UPC)
        .containsEntry("student", student.toString())
        .containsEntry("credits", 2)
        .containsEntry("sent_at", null);
    assertThat(event.get("occurred")).isNotNull();
  }

  @Test
  @DisplayName("A student who used the monthly limit is told the limit and credits used")
  void aStudentAtTheLimitIsToldTheLimitAndCreditsUsed() throws Exception {
    purchase(student, 5, "full-" + UUID.randomUUID()).andExpect(status().isCreated());
    assertThat(confirmedCreditsThisMonth(student)).isEqualTo(5);

    purchase(student, 1, "over-" + UUID.randomUUID())
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Monthly purchase limit is 5 credits; 5 already used; maximum available now is 0"));
  }

  @Test
  @DisplayName("A purchase above the remaining allowance reports the maximum amount")
  void aPurchaseAboveTheRemainingLimitReportsTheMaximum() throws Exception {
    purchase(student, 4, "partial-" + UUID.randomUUID()).andExpect(status().isCreated());
    assertThat(confirmedCreditsThisMonth(student)).isEqualTo(4);

    purchase(student, 2, "too-many-" + UUID.randomUUID())
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Monthly purchase limit is 5 credits; 4 already used; maximum available now is 1"));
  }

  @Test
  @DisplayName("A rejected payment does not consume credits or the monthly allowance")
  void aRejectedPaymentDoesNotConsumeCreditsOrTheLimit() throws Exception {
    when(provider.charge(any(), any(BigDecimal.class), any()))
        .thenReturn(
            new PaymentProvider.PaymentResult(
                PaymentProvider.PaymentResult.Outcome.REJECTED, "declined"));

    purchase(student, 5, "declined-" + UUID.randomUUID())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.confirmedAt").doesNotExist());
    assertThat(creditsOwedToTheWallet(student)).isZero();

    when(provider.charge(any(), any(BigDecimal.class), any()))
        .thenReturn(
            new PaymentProvider.PaymentResult(
                PaymentProvider.PaymentResult.Outcome.CONFIRMED, "accepted"));
    purchase(student, 5, "retry-" + UUID.randomUUID())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    assertThat(creditsOwedToTheWallet(student)).isEqualTo(5);
  }

  @Test
  @DisplayName("A repeated idempotency key does not credit twice")
  void aRepeatedIdempotencyKeyDoesNotCreditTwice() throws Exception {
    String key = "repeat-" + UUID.randomUUID();
    String first =
        purchase(student, 2, key)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    purchase(student, 2, key)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")))
        .andExpect(jsonPath("$.credits").value(2));

    assertThat(creditsOwedToTheWallet(student)).isEqualTo(2);
    assertThat(confirmedEventsOf(UUID.fromString(JsonPath.read(first, "$.id")))).isEqualTo(1);
  }

  @Test
  @DisplayName("An idempotency key cannot be reused for a different amount")
  void anIdempotencyKeyCannotBeReusedForAnotherAmount() throws Exception {
    String key = "changed-" + UUID.randomUUID();
    purchase(student, 2, key).andExpect(status().isCreated());

    purchase(student, 3, key)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value("This idempotency key was already used for a different purchase"));
    assertThat(creditsOwedToTheWallet(student)).isEqualTo(2);
  }

  @Test
  @DisplayName("The student lists purchases and gets a confirmed purchase receipt")
  void aStudentCanViewPurchaseDetailsAndReceipt() throws Exception {
    UUID purchaseId = aConfirmedPurchaseOf(student, UPC, 2);

    mockMvc
        .perform(asMonolith(get("/api/v1/payments/purchases"), UPC, student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(purchaseId.toString()))
        .andExpect(jsonPath("$[0].credits").value(2))
        .andExpect(jsonPath("$[0].amount").value(10.00))
        .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
        .andExpect(jsonPath("$[0].createdAt").exists());

    mockMvc
        .perform(asMonolith(get("/api/v1/payments/purchases/{id}", purchaseId), UPC, student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(purchaseId.toString()))
        .andExpect(jsonPath("$.credits").value(2))
        .andExpect(jsonPath("$.amount").value(10.00))
        .andExpect(jsonPath("$.currency").value("PEN"))
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.purchasedAt").exists())
        .andExpect(jsonPath("$.confirmedAt").exists())
        .andExpect(jsonPath("$.providerReference").value("test-" + purchaseId))
        .andExpect(jsonPath("$.creditMovement.type").value("PURCHASE"))
        .andExpect(jsonPath("$.creditMovement.referenceId").value(purchaseId.toString()))
        .andExpect(
            jsonPath("$.creditMovement.historyUrl")
                .value("/api/v1/wallet/movements?reason=PURCHASE"));
  }

  @Test
  @DisplayName("A student cannot read another student's purchase")
  void aStudentCannotViewAnotherStudentsPurchaseDetails() throws Exception {
    UUID purchaseId = aConfirmedPurchaseOf(student, UPC, 2);

    mockMvc
        .perform(
            asMonolith(get("/api/v1/payments/purchases/{id}", purchaseId), UPC, UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Purchase not found"));
  }

  @Test
  @DisplayName("A university cannot read another university's purchase")
  void aUniversityCannotReadAnotherUniversitysPurchase() throws Exception {
    UUID purchaseId = aConfirmedPurchaseOf(student, UPC, 2);

    mockMvc
        .perform(asMonolith(get("/api/v1/payments/purchases"), USIL, student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(asMonolith(get("/api/v1/payments/purchases/{id}", purchaseId), USIL, student))
        .andExpect(status().isNotFound());
    // Row level security on its own, with no filter of the query to help it: bound to USIL, the
    // table answers nothing of UPC's.
    assertThat(TenantContext.callAs(USIL, () -> purchases.findById(purchaseId))).isEmpty();
    assertThat(purchases.findById(purchaseId)).isEmpty();
    assertThat(TenantContext.callAs(UPC, () -> purchases.findById(purchaseId))).isPresent();
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
    TenantContext.runAs(UPC, () -> purchases.save(priorMonthPurchase));

    purchase(student, 5, "new-month-" + UUID.randomUUID())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    assertThat(creditsOwedToTheWallet(student)).isEqualTo(5);
  }

  @Test
  @DisplayName("Concurrent purchases cannot exceed the monthly allowance")
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

      assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(201, 409);
      assertThat(confirmedCreditsThisMonth(student)).isEqualTo(5);
      assertThat(creditsOwedToTheWallet(student)).isEqualTo(5);
    } finally {
      executor.shutdownNow();
    }
  }

  private static final String STILL_PROCESSING =
      "Payment is still being processed. Do not start another purchase; check this purchase's status.";

  /**
   * A purchase of two credits the provider answers {@code PENDING}, and confirms when
   * reconciliation asks for it later, as if the student had left the platform in between.
   */
  private UUID aPurchaseTheProviderConfirmsLater(UUID who, String tenantId) throws Exception {
    when(provider.charge(any(), any(BigDecimal.class), any()))
        .thenAnswer(
            invocation ->
                new PaymentProvider.PaymentResult(
                    PaymentProvider.PaymentResult.Outcome.PENDING,
                    "provider-" + invocation.getArgument(0)));
    when(provider.status(any()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new PaymentProvider.PaymentResult(
                        PaymentProvider.PaymentResult.Outcome.CONFIRMED,
                        "provider-" + invocation.getArgument(0))));

    String response =
        purchase(who, tenantId, 2, "offline-" + UUID.randomUUID())
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.guidance").value(STILL_PROCESSING))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.id"));
  }

  @Test
  @DisplayName("A purchase is confirmed after the student leaves the platform")
  void aPurchaseIsConfirmedAfterTheStudentLeavesThePlatform() throws Exception {
    UUID purchaseId = aPurchaseTheProviderConfirmsLater(student, UPC);
    assertThat(creditsOwedToTheWallet(student)).isZero();

    reconciler.reconcile();

    mockMvc
        .perform(asMonolith(get("/api/v1/payments/purchases"), UPC, student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(purchaseId.toString()))
        .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    assertThat(creditsOwedToTheWallet(student)).isEqualTo(2);
    assertThat(confirmedEventsOf(purchaseId)).isEqualTo(1);
  }

  @Test
  @DisplayName("The student is told a pending payment is still being processed")
  void theStudentIsToldAPendingPaymentIsStillBeingProcessed() throws Exception {
    aPurchaseTheProviderConfirmsLater(student, UPC);

    mockMvc
        .perform(asMonolith(get("/api/v1/payments/purchases"), UPC, student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("PENDING"))
        .andExpect(jsonPath("$[0].guidance").value(STILL_PROCESSING));
    assertThat(creditsOwedToTheWallet(student)).isZero();
  }

  @Test
  @DisplayName("Repeated provider confirmation does not credit twice")
  void repeatedProviderConfirmationDoesNotCreditTwice() throws Exception {
    UUID purchaseId = aPurchaseTheProviderConfirmsLater(student, UPC);
    reconciler.reconcile();
    assertThat(creditsOwedToTheWallet(student)).isEqualTo(2);

    reconciler.reconcile();

    assertThat(creditsOwedToTheWallet(student)).isEqualTo(2);
    assertThat(confirmedEventsOf(purchaseId)).isEqualTo(1);
  }

  @Test
  @DisplayName("Reconciliation visits every university with pending purchases and only its own rows")
  void reconciliationVisitsEveryUniversityWithPendingPurchases() throws Exception {
    UUID otherStudent = UUID.randomUUID();
    UUID ofUpc = aPurchaseTheProviderConfirmsLater(student, UPC);
    UUID ofUsil = aPurchaseTheProviderConfirmsLater(otherStudent, USIL);

    assertThat(purchases.tenantsWithPendingPurchases()).contains(UPC, USIL);
    // Nothing is bound: the job learns the universities from its own database, not from identity.
    assertThat(TenantContext.get()).isNull();
    reconciler.reconcile();

    assertThat(owner.queryForObject("select status from payments.purchases where id = ?", String.class, ofUpc))
        .isEqualTo("CONFIRMED");
    assertThat(owner.queryForObject("select status from payments.purchases where id = ?", String.class, ofUsil))
        .isEqualTo("CONFIRMED");
    assertThat(
            owner.queryForObject(
                "select tenant_id from payments.outbox where aggregate_id = ?", String.class, ofUsil))
        .isEqualTo(USIL);
  }

  @Test
  @DisplayName("The rest of the platform remains available when the provider is down")
  void aProviderOutageLeavesThePurchasePendingWithoutBlockingOtherApis() throws Exception {
    doThrow(new PaymentProviderUnavailable("Provider timeout"))
        .when(provider)
        .charge(any(), any(BigDecimal.class), any());

    purchase(student, 2, "unavailable-" + UUID.randomUUID())
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.providerUnavailable").value(true))
        .andExpect(
            jsonPath("$.guidance")
                .value(
                    "Credit purchases are temporarily unavailable. This attempt remains pending; do not retry. Check this purchase's status."));

    mockMvc
        .perform(asMonolith(get("/api/v1/payments/purchases"), UPC, student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("PENDING"));
    assertThat(creditsOwedToTheWallet(student)).isZero();
  }

  @Test
  @DisplayName("A purchase with no result expires and releases its monthly allowance")
  void anUnconfirmedPurchaseExpiresWithoutConsumingTheMonthlyLimit() throws Exception {
    Instant now = clock.instant();
    Instant createdAt = now.minus(Duration.ofHours(2));
    Purchase pending =
        Purchase.pending(
            UUID.randomUUID(),
            UPC,
            student,
            5,
            new BigDecimal("25.00"),
            "PEN",
            "expired-" + UUID.randomUUID(),
            now.minusSeconds(1),
            createdAt);
    TenantContext.runAs(UPC, () -> purchases.save(pending));

    reconciler.reconcile();

    assertThat(
            TenantContext.callAs(UPC, () -> purchases.findById(pending.getId()))
                .orElseThrow()
                .getStatus())
        .isEqualTo(PurchaseStatus.EXPIRED);
    assertThat(creditsOwedToTheWallet(student)).isZero();
    purchase(student, 5, "after-expiry-" + UUID.randomUUID())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    assertThat(creditsOwedToTheWallet(student)).isEqualTo(5);
  }

  @Test
  @DisplayName("An invalid purchase request is refused with the answers of the monolith")
  void anInvalidPurchaseRequestIsRefused() throws Exception {
    mockMvc
        .perform(
            asMonolith(post("/api/v1/payments/purchases"), UPC, student)
                .header("Idempotency-Key", "zero")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credits\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The purchase request is invalid"));
    mockMvc
        .perform(
            asMonolith(post("/api/v1/payments/purchases"), UPC, student)
                .header("Idempotency-Key", "x".repeat(65))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credits\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The purchase request is invalid"));
    mockMvc
        .perform(
            asMonolith(post("/api/v1/payments/purchases"), UPC, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credits\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The header Idempotency-Key is required"));
    mockMvc
        .perform(
            asMonolith(post("/api/v1/payments/purchases"), UPC, student)
                .header("Idempotency-Key", "not-json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The purchase request is invalid"))
        .andExpect(jsonPath("$.path").value("/api/v1/payments/purchases"));
    mockMvc
        .perform(asMonolith(get("/api/v1/payments/purchases"), null, student))
        .andExpect(status().isBadRequest());
  }

  private UUID aConfirmedPurchaseOf(UUID who, String tenantId, int credits) throws Exception {
    String response =
        purchase(who, tenantId, credits, "receipt-" + UUID.randomUUID())
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.id"));
  }

  private ResultActions purchase(UUID user, int credits, String idempotencyKey) throws Exception {
    return purchase(user, UPC, credits, idempotencyKey);
  }

  private ResultActions purchase(UUID user, String tenantId, int credits, String idempotencyKey)
      throws Exception {
    return mockMvc.perform(
        asMonolith(post("/api/v1/payments/purchases"), tenantId, user)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"credits\":" + credits + "}"));
  }

  /** The credits the outbox owes the wallet for the student: what the monolith will credit. */
  private int creditsOwedToTheWallet(UUID user) {
    return owner.queryForObject(
        """
        select coalesce(sum((payload->>'credits')::int), 0)
        from payments.outbox
        where event_type = 'PurchaseConfirmed' and payload->>'studentId' = ?
        """,
        Integer.class,
        user.toString());
  }

  private int confirmedEventsOf(UUID purchaseId) {
    return owner.queryForObject(
        "select count(*) from payments.outbox where event_type = 'PurchaseConfirmed'"
            + " and aggregate_id = ?",
        Integer.class,
        purchaseId);
  }

  private long confirmedCreditsThisMonth(UUID user) {
    YearMonth month = YearMonth.from(clock.instant().atZone(ZoneOffset.UTC));
    return owner.queryForObject(
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
