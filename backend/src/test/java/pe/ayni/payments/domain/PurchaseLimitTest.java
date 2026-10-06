package pe.ayni.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.payments.domain.model.PurchaseLimit;
import pe.ayni.payments.domain.model.PurchaseLimitExceeded;
import pe.ayni.payments.domain.model.PurchaseRuleViolation;

class PurchaseLimitTest {

  private final PurchaseLimit limit = new PurchaseLimit(5);

  @Test
  @DisplayName("A purchase up to the remaining monthly allowance is allowed")
  void purchaseWithinTheRemainingLimitIsAllowed() {
    limit.check(2, 3);
    assertThat(limit.remainingAfter(3)).isEqualTo(2);
  }

  @Test
  @DisplayName("The limit reports the maximum available amount when a purchase exceeds it")
  void purchaseAboveTheRemainingLimitReportsTheMaximum() {
    assertThatThrownBy(() -> limit.check(3, 4))
        .isInstanceOf(PurchaseLimitExceeded.class)
        .satisfies(
            error -> {
              PurchaseLimitExceeded exceeded = (PurchaseLimitExceeded) error;
              assertThat(exceeded.limit()).isEqualTo(5);
              assertThat(exceeded.used()).isEqualTo(4);
              assertThat(exceeded.maximumNow()).isEqualTo(1);
            });
  }

  @Test
  @DisplayName("An exhausted allowance reports that no credits can be bought")
  void exhaustedLimitHasNoRemainingCredits() {
    assertThatThrownBy(() -> limit.check(1, 5))
        .isInstanceOf(PurchaseLimitExceeded.class)
        .hasMessageContaining("5 already used")
        .hasMessageContaining("maximum available now is 0");
  }

  @Test
  @DisplayName("A purchase cannot request zero or a negative number of credits")
  void purchaseMustRequestAtLeastOneCredit() {
    assertThatThrownBy(() -> limit.check(0, 0))
        .isInstanceOf(PurchaseRuleViolation.class)
        .hasMessageContaining("at least one credit");
  }
}
