package pe.ayni.wallet.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.wallet.domain.model.CreditUse;
import pe.ayni.wallet.domain.model.CreditUseKind;

public interface CreditUseRepository extends JpaRepository<CreditUse, UUID> {

  Optional<CreditUse> findByTenantIdAndStudentIdAndIdempotencyKey(
      String tenantId, UUID studentId, String idempotencyKey);

  List<CreditUse> findByTenantIdAndStudentIdAndKindOrderByCreatedAtDesc(
      String tenantId, UUID studentId, CreditUseKind kind);

  @Query(
      """
      select coalesce(sum(use.credits), 0) from CreditUse use
      where use.tenantId = :tenantId
        and use.kind = pe.ayni.wallet.domain.model.CreditUseKind.INCOMING_STUDENT_DONATION
      """)
  Long totalDonated(@Param("tenantId") String tenantId);

  @Query(
      """
      select count(use) from CreditUse use
      where use.tenantId = :tenantId
        and use.kind = pe.ayni.wallet.domain.model.CreditUseKind.INCOMING_STUDENT_DONATION
      """)
  long donationCount(@Param("tenantId") String tenantId);
}
