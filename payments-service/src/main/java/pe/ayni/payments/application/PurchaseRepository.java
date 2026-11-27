package pe.ayni.payments.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseStatus;

/**
 * Where purchases are kept: {@code payments.purchases} of {@code ayni_payments}.
 *
 * <p>Every method runs inside a transaction of the university bound to the thread, so row level
 * security answers only that university's rows even if a filter below were forgotten.
 */
public interface PurchaseRepository {

  /** Inserts a new purchase or stores the changes of one that exists. */
  Purchase save(Purchase purchase);

  Optional<Purchase> findById(UUID id);

  Optional<Purchase> findByTenantIdAndStudentIdAndIdempotencyKey(
      String tenantId, UUID studentId, String idempotencyKey);

  Optional<Purchase> findByTenantIdAndStudentIdAndId(String tenantId, UUID studentId, UUID id);

  /** Locks the purchase until the transaction ends, so two results are not applied at once. */
  Optional<Purchase> findByTenantIdAndId(String tenantId, UUID id);

  List<Purchase> findByTenantIdAndStatusOrderByCreatedAtAsc(String tenantId, PurchaseStatus status);

  List<Purchase> findByTenantIdAndStudentIdOrderByCreatedAtDesc(String tenantId, UUID studentId);

  /**
   * The credits a student has confirmed in the month, plus those still pending that may yet be
   * confirmed in it.
   */
  Long sumReservedCredits(String tenantId, UUID studentId, Instant from, Instant to);

  /**
   * The universities that have at least one pending purchase, and nothing else about them: what the
   * reconciliation job needs to know whom to bind, read through a function of the owner because no
   * university is bound yet (see the migration that enables row level security).
   */
  List<String> tenantsWithPendingPurchases();
}
