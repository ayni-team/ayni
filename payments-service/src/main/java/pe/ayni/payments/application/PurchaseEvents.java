package pe.ayni.payments.application;

import pe.ayni.payments.domain.model.Purchase;

/**
 * What the rest of Ayni is told about purchases. The wallet lives in the monolith, so a confirmed
 * purchase is a fact this service owes it.
 *
 * <p>Called inside the transaction that confirms the purchase: the fact is recorded with the change
 * that produced it, or neither is (Transactional Outbox, ADR 0011). Delivering it is somebody
 * else's job, which retries until the monolith accepts it.
 */
public interface PurchaseEvents {

  /** Records that the purchase was confirmed; the same purchase is recorded only once. */
  void purchaseConfirmed(Purchase purchase);
}
