package pe.ayni.skills.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.domain.model.ValidationStatus;

/** Every method takes the university: a coordinator never reads another one's queue. */
public interface ValidationRequestRepository extends JpaRepository<ValidationRequest, UUID> {

  Optional<ValidationRequest> findByTenantIdAndId(String tenantId, UUID id);

  /** One page of the requests in the given status, for the coordinator's queue. */
  Page<ValidationRequest> findByTenantIdAndStatus(
      String tenantId, ValidationStatus status, Pageable pageable);

  /**
   * One request, locked until the transaction ends.
   *
   * <p>Two coordinators deciding the same request at once would both read it as waiting, and the
   * student would be told twice, possibly two opposite things. The second one waits and then finds
   * it already decided.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select request from ValidationRequest request
      where request.tenantId = :tenantId and request.id = :id
      """)
  Optional<ValidationRequest> lockByTenantIdAndId(
      @Param("tenantId") String tenantId, @Param("id") UUID id);

  /** Every submission made for the given skills, to show where each one stands. */
  List<ValidationRequest> findByTenantIdAndOfferedSkillIdIn(
      String tenantId, Collection<UUID> offeredSkillIds);
}
