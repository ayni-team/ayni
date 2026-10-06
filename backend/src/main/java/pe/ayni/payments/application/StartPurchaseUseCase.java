package pe.ayni.payments.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseIdempotencyConflict;
import pe.ayni.payments.domain.model.PurchaseLimit;
import pe.ayni.payments.infrastructure.PurchaseLimitLock;
import pe.ayni.payments.infrastructure.PurchaseRepository;
import pe.ayni.shared.tenancy.TenantContext;

@Service
class StartPurchaseUseCase {

  static final String CURRENCY = "PEN";

  private final PurchaseRepository purchases;
  private final PurchaseLimitLock purchaseLimitLock;
  private final Clock clock;
  private final BigDecimal pricePerCredit;
  private final PurchaseLimit limit;
  private final Duration pendingTimeout;

  StartPurchaseUseCase(
      PurchaseRepository purchases,
      PurchaseLimitLock purchaseLimitLock,
      Clock clock,
      @Value("${ayni.payments.price-per-credit:5.00}") BigDecimal pricePerCredit,
      @Value("${ayni.payments.monthly-credit-limit:5}") int monthlyCreditLimit,
      @Value("${ayni.payments.pending-timeout:PT24H}") Duration pendingTimeout) {
    this.purchases = purchases;
    this.purchaseLimitLock = purchaseLimitLock;
    this.clock = clock;
    this.pricePerCredit =
        Objects.requireNonNull(pricePerCredit, "pricePerCredit must not be null")
            .setScale(2, RoundingMode.UNNECESSARY);
    if (this.pricePerCredit.signum() <= 0) {
      throw new IllegalArgumentException("pricePerCredit must be greater than zero");
    }
    this.limit = new PurchaseLimit(monthlyCreditLimit);
    this.pendingTimeout = Objects.requireNonNull(pendingTimeout, "pendingTimeout must not be null");
    if (pendingTimeout.isZero() || pendingTimeout.isNegative()) {
      throw new IllegalArgumentException("pendingTimeout must be greater than zero");
    }
  }

  @Transactional
  PurchaseOutcome execute(UUID studentId, int credits, String idempotencyKey) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    String tenantId = TenantContext.require();

    purchaseLimitLock.acquire(tenantId, studentId);
    var previous =
        purchases.findByTenantIdAndStudentIdAndIdempotencyKey(
            tenantId, studentId, idempotencyKey);
    if (previous.isPresent()) {
      Purchase purchase = previous.get();
      if (purchase.getCredits() != credits) {
        throw new PurchaseIdempotencyConflict();
      }
      return PurchaseOutcome.of(purchase, false);
    }

    Instant now = clock.instant();
    YearMonth month = YearMonth.from(now.atZone(ZoneOffset.UTC));
    Instant monthStart = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    long reservedCredits =
        Objects.requireNonNullElse(
            purchases.sumReservedCredits(tenantId, studentId, monthStart, nextMonthStart), 0L);
    limit.check(credits, reservedCredits);

    BigDecimal amount = pricePerCredit.multiply(BigDecimal.valueOf(credits));
    Purchase purchase =
        purchases.save(
            Purchase.pending(
                UUID.randomUUID(),
                tenantId,
                studentId,
                credits,
                amount,
                CURRENCY,
                idempotencyKey,
                now.plus(pendingTimeout),
                now));
    return PurchaseOutcome.of(purchase, true);
  }
}
