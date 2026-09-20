package pe.ayni.wallet.domain.model;

/**
 * What a ledger entry points at outside wallet.
 *
 * <p>Only the identifier is kept, never a foreign key: the booking, the session and the purchase
 * belong to other modules and other schemas. Whoever needs the rest asks that module for it.
 */
public enum ReferenceType {
  BOOKING,
  SESSION,
  PURCHASE,
  POLICY
}
