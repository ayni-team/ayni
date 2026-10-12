package pe.ayni.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.domain.model.CreditRuleViolation;
import pe.ayni.wallet.domain.model.CreditUseConfirmation;
import pe.ayni.wallet.domain.model.CreditUseKind;

class CreditUseConfirmationTest {

  @Test
  void confirmationExpiresAtItsDeadlineAndCanOnlyBeUsedOnce() {
    Instant expiresAt = Instant.parse("2026-10-06T14:10:00Z");
    CreditUseConfirmation confirmation =
        CreditUseConfirmation.create(
            UUID.randomUUID(),
            "UPC",
            UUID.randomUUID(),
            CreditUseKind.INCOMING_STUDENT_DONATION,
            null,
            null,
            Credits.of(2),
            expiresAt);

    assertThat(confirmation.isExpiredAt(expiresAt.minusNanos(1))).isFalse();
    assertThat(confirmation.isExpiredAt(expiresAt)).isTrue();
    confirmation.confirm(expiresAt.minusSeconds(1));

    assertThatThrownBy(() -> confirmation.confirm(expiresAt))
        .isInstanceOf(CreditRuleViolation.class)
        .hasMessage("This credit-use confirmation has already been used");
  }
}
