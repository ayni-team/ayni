package pe.ayni.wallet.domain.model;

/**
 * Why a movement happened.
 *
 * <p>The ledger is append only, so a mistake is not edited away: it is answered with a new {@link
 * #ADJUSTMENT} entry that says what the correction is and leaves the mistake visible.
 */
public enum LedgerReason {

  /** Credits granted by the university's policy. */
  GRANT,

  /** Credits spent confirming a booking. */
  BOOKING_CHARGE,

  /** Credits returned because a booking was cancelled. */
  BOOKING_REFUND,

  /** Credits earned by teaching a session. */
  SESSION_EARNING,

  /** Credits that reached their expiry unspent. */
  EXPIRY,

  /** Credits bought by the student. */
  PURCHASE,

  /** A correction. The entry it corrects stays where it is. */
  ADJUSTMENT
}
