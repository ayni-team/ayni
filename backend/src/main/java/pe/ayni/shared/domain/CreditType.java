package pe.ayni.shared.domain;

/**
 * Origin of a group of credits.
 *
 * <p>The distinction carries the central rule of the product, and it is written here once so that
 * no module has to remember it: only credits earned by teaching count towards recognition, and they
 * are the only ones that never expire.
 */
public enum CreditType {

  /** Granted to every student of the university when they join. Expires after the policy's validity. */
  SEED,

  /** Granted by the university to a targeted group. Expires after the policy's validity. */
  ALLOCATED,

  /** Obtained by teaching a session. Never expires and counts towards recognition. */
  EARNED,

  /** Bought by the student. Does not expire: nobody should lose what they paid for. */
  PURCHASED;

  /** Whether credits of this type have an expiry date. Only what the university grants does. */
  public boolean expires() {
    return this == SEED || this == ALLOCATED;
  }

  /** Whether credits of this type count towards academic recognition. */
  public boolean countsTowardsRecognition() {
    return this == EARNED;
  }
}
