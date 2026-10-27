package pe.ayni.wallet.domain.model;

import java.util.UUID;
import pe.ayni.shared.domain.Credits;

/**
 * What happened to an account, before it is written down.
 *
 * <p>A movement is the business fact: these credits left that group, for this reason. The {@link
 * LedgerEntry} is the same fact once it has taken its place in the chain, with a sequence number
 * and a hash. Keeping them apart is what lets the rules be decided without knowing the position an
 * entry will end up in.
 *
 * @param lotId the group the credits moved in or out of, {@code null} only for an adjustment that
 *     does not belong to any group
 * @param referenceId what caused it outside wallet: the booking, the session, the purchase
 */
public record Movement(
    String tenantId,
    UUID accountId,
    UUID lotId,
    LedgerDirection direction,
    Credits amount,
    LedgerReason reason,
    ReferenceType referenceType,
    UUID referenceId) {

  public Movement {
    if (amount == null || amount.isZero()) {
      // The ledger records what moved, and nothing moving is not a movement.
      throw new CreditRuleViolation("A movement of zero credits is not a movement");
    }
  }

  /** Credits leaving the account. */
  public static Movement debit(
      CreditLot lot,
      Credits amount,
      LedgerReason reason,
      ReferenceType referenceType,
      UUID referenceId) {
    return new Movement(
        lot.tenantId(),
        lot.accountId(),
        lot.id(),
        LedgerDirection.DEBIT,
        amount,
        reason,
        referenceType,
        referenceId);
  }

  /** Credits arriving in the account. */
  public static Movement credit(
      CreditLot lot,
      Credits amount,
      LedgerReason reason,
      ReferenceType referenceType,
      UUID referenceId) {
    return new Movement(
        lot.tenantId(),
        lot.accountId(),
        lot.id(),
        LedgerDirection.CREDIT,
        amount,
        reason,
        referenceType,
        referenceId);
  }
}
