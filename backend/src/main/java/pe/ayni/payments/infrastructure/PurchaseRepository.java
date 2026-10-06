package pe.ayni.payments.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.payments.domain.model.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

  Optional<Purchase> findByTenantIdAndStudentIdAndIdempotencyKey(
      String tenantId, UUID studentId, String idempotencyKey);

  @Query(
      """
      select coalesce(sum(p.credits), 0)
      from Purchase p
      where p.tenantId = :tenantId
        and p.studentId = :studentId
        and p.status = pe.ayni.payments.domain.model.PurchaseStatus.CONFIRMED
        and p.confirmedAt >= :from
        and p.confirmedAt < :to
      """)
  Long sumConfirmedCredits(
      @Param("tenantId") String tenantId,
      @Param("studentId") UUID studentId,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
