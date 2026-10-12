package pe.ayni.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.wallet.domain.model.CampusBenefit;
import pe.ayni.wallet.domain.model.CreditRuleViolation;

class CampusBenefitTest {

  @Test
  void trimsBenefitTextAndAllowsDeactivationWithoutChangingItsIdentity() {
    Instant created = Instant.parse("2026-10-06T14:00:00Z");
    CampusBenefit benefit =
        CampusBenefit.create(
            UUID.randomUUID(), "UPC", "  Meal voucher  ", "  One lunch  ", 2, true, created);

    benefit.update("Meal voucher", "Lunch on campus", 3, false, created.plusSeconds(60));

    assertThat(benefit.name()).isEqualTo("Meal voucher");
    assertThat(benefit.description()).isEqualTo("Lunch on campus");
    assertThat(benefit.creditsCost()).isEqualTo(3);
    assertThat(benefit.active()).isFalse();
    assertThat(benefit.createdAt()).isEqualTo(created);
    assertThat(benefit.updatedAt()).isEqualTo(created.plusSeconds(60));
  }

  @Test
  void rejectsAnEmptyBenefitNameAndZeroCreditCost() {
    Instant now = Instant.parse("2026-10-06T14:00:00Z");

    assertThatThrownBy(
            () ->
                CampusBenefit.create(
                    UUID.randomUUID(), "UPC", "  ", "Campus benefit", 1, true, now))
        .isInstanceOf(CreditRuleViolation.class);
    assertThatThrownBy(
            () ->
                CampusBenefit.create(
                    UUID.randomUUID(), "UPC", "Printing", "Campus benefit", 0, true, now))
        .isInstanceOf(CreditRuleViolation.class)
        .hasMessage("A campus benefit must cost at least one earned credit");
  }
}
