package pe.ayni.wallet.domain.model;

import pe.ayni.shared.domain.CreditType;

/**
 * What put a group of credits in an account.
 *
 * <p>The origin decides how the movement is written in the ledger, so the mapping lives here once
 * instead of in every use case that grants credits.
 */
public enum CreditSource {

  /** The university's credit policy: the initial grant and targeted allocations. */
  POLICY(LedgerReason.GRANT, ReferenceType.POLICY),

  /** A session taught. These are the credits that count towards recognition. */
  SESSION(LedgerReason.SESSION_EARNING, ReferenceType.SESSION),

  /** A purchase confirmed by the payment provider. */
  PURCHASE(LedgerReason.PURCHASE, ReferenceType.PURCHASE),

  /**
   * Reserved for a refund that cannot return to the group it came from.
   *
   * <p>Nothing produces it today: a refund restores the original group, with the expiry it had, so
   * that credits do not gain life by being refunded. It exists because the moment a group is ever
   * archived, a refund will need somewhere to land.
   */
  REFUND(LedgerReason.BOOKING_REFUND, ReferenceType.BOOKING);

  private final LedgerReason reason;
  private final ReferenceType referenceType;

  CreditSource(LedgerReason reason, ReferenceType referenceType) {
    this.reason = reason;
    this.referenceType = referenceType;
  }

  /** How the arrival of these credits is recorded in the ledger. */
  public LedgerReason reason() {
    return reason;
  }

  /** What the ledger entry points at: the policy, the session, the purchase. */
  public ReferenceType referenceType() {
    return referenceType;
  }

  /** The origin that grants credits of the given type. */
  public static CreditSource of(CreditType type) {
    return switch (type) {
      case SEED, ALLOCATED -> POLICY;
      case EARNED -> SESSION;
      case PURCHASED -> PURCHASE;
    };
  }
}
