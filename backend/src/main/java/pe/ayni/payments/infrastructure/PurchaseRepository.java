package pe.ayni.payments.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseStatus;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

  Optional<Purchase> findByTenantIdAndStudentIdAndIdempotencyKey(
      String tenantId, UUID studentId, String idempotencyKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Purchase> findByTenantIdAndId(String tenantId, UUID id);

  List<Purchase> findByTenantIdAndStatusOrderByCreatedAtAsc(String tenantId, PurchaseStatus status);

  List<Purchase> findByTenantIdAndStudentIdOrderByCreatedAtDesc(String tenantId, UUID studentId);

  @Query(
      """
      select coalesce(sum(p.credits), 0)
      from Purchase p
      where p.tenantId = :tenantId
        and p.studentId = :studentId
        and (
          (p.status = pe.ayni.payments.domain.model.PurchaseStatus.CONFIRMED
            and p.confirmedAt >= :from and p.confirmedAt < :to)
          or
          (p.status = pe.ayni.payments.domain.model.PurchaseStatus.PENDING
            and (
              (p.createdAt >= :from and p.createdAt < :to)
              or (p.createdAt < :from and p.expiresAt >= :from)
            ))
        )
      """)
  Long sumReservedCredits(
      @Param("tenantId") String tenantId,
      @Param("studentId") UUID studentId,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
