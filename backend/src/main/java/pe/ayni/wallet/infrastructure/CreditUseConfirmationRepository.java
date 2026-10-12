package pe.ayni.wallet.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.wallet.domain.model.CreditUseConfirmation;

public interface CreditUseConfirmationRepository
    extends JpaRepository<CreditUseConfirmation, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select confirmation from CreditUseConfirmation confirmation
      where confirmation.tenantId = :tenantId
        and confirmation.studentId = :studentId
        and confirmation.id = :id
      """)
  Optional<CreditUseConfirmation> lockByTenantStudentAndId(
      @Param("tenantId") String tenantId,
      @Param("studentId") UUID studentId,
      @Param("id") UUID id);
}
