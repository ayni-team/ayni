package pe.ayni.wallet.domain.model;

import pe.ayni.shared.domain.Credits;

/**
 * How many credits a single group contributes to an operation.
 *
 * <p>A charge of three credits may take two from the group expiring in October and one from the
 * group expiring in December. Each of those pairs is an allocation, and each becomes one ledger
 * entry, which is what lets a refund know where every credit came from.
 */
public record Allocation(CreditLot lot, Credits amount) {

  public Allocation {
    if (amount == null || amount.isZero()) {
      throw new CreditRuleViolation("An allocation of zero credits is not an allocation");
    }
  }
}
