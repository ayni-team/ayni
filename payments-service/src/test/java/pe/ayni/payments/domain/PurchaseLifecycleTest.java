package pe.ayni.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseRuleViolation;
import pe.ayni.payments.domain.model.PurchaseStatus;

class PurchaseLifecycleTest {

  private static final Instant CREATED_AT = Instant.parse("2026-10-06T12:00:00Z");

  @Test
  @DisplayName("A pending purchase expires at its persisted deadline")
  void pendingPurchaseExpiresAtItsDeadline() {
    Purchase purchase = pending();

    assertThat(purchase.expire(CREATED_AT.plus(Duration.ofHours(23)))).isFalse();
    assertThat(purchase.expire(CREATED_AT.plus(Duration.ofHours(24)))).isTrue();
    assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
  }

  @Test
  @DisplayName("An expired purchase cannot be confirmed later")
  void expiredPurchaseCannotBeConfirmed() {
    Purchase purchase = pending();
    purchase.expire(CREATED_AT.plus(Duration.ofHours(24)));

    assertThatThrownBy(
            () -> purchase.confirm("provider-reference", CREATED_AT.plus(Duration.ofHours(25))))
        .isInstanceOf(PurchaseRuleViolation.class)
        .hasMessageContaining("Only a pending purchase");
  }

  @Test
  @DisplayName("The provider reference cannot change while reconciling a purchase")
  void providerReferenceCannotChangeWhileReconciling() {
    Purchase purchase = pending();
    purchase.awaitProviderResult("provider-reference");

    assertThatThrownBy(() -> purchase.confirm("different-reference", CREATED_AT.plusSeconds(1)))
        .isInstanceOf(PurchaseRuleViolation.class)
        .hasMessageContaining("does not match this purchase");
  }

  private static Purchase pending() {
    return Purchase.pending(
        UUID.randomUUID(),
        "UPC",
        UUID.randomUUID(),
        2,
        new BigDecimal("10.00"),
        "PEN",
        "idempotency-" + UUID.randomUUID(),
        CREATED_AT.plus(Duration.ofHours(24)),
        CREATED_AT);
  }
}
