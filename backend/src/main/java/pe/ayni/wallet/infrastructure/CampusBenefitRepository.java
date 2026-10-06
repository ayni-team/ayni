package pe.ayni.wallet.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.wallet.domain.model.CampusBenefit;

public interface CampusBenefitRepository extends JpaRepository<CampusBenefit, UUID> {

  List<CampusBenefit> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select benefit from CampusBenefit benefit
      where benefit.tenantId = :tenantId and benefit.id = :id
      """)
  Optional<CampusBenefit> lockByTenantIdAndId(
      @Param("tenantId") String tenantId, @Param("id") UUID id);
}
