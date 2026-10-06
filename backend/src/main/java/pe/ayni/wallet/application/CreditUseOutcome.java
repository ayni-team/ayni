package pe.ayni.wallet.application;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.domain.model.CreditUse;
import pe.ayni.wallet.domain.model.CreditUseKind;

public record CreditUseOutcome(
    UUID id,
    UUID confirmationId,
    CreditUseKind kind,
    String benefitName,
    Credits credits,
    Instant createdAt,
    boolean confirmationRequired,
    long donationPoolBalance) {

  static CreditUseOutcome confirmation(
      UUID confirmationId,
      CreditUseKind kind,
      String benefitName,
      Credits credits,
      long poolBalance) {
    return new CreditUseOutcome(
        null, confirmationId, kind, benefitName, credits, null, true, poolBalance);
  }

  static CreditUseOutcome completed(CreditUse use, long poolBalance) {
    return new CreditUseOutcome(
        use.id(),
        null,
        use.kind(),
        use.benefitName(),
        use.credits(),
        use.createdAt(),
        false,
        poolBalance);
  }
}
