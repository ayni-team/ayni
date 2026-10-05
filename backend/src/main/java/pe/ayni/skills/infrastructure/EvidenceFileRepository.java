package pe.ayni.skills.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.skills.domain.model.EvidenceFile;

/** Every method takes the university: the files of a request are read with the request's tenant. */
public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, UUID> {

  List<EvidenceFile> findByTenantIdAndValidationRequestId(String tenantId, UUID validationRequestId);

  /** The files of several requests at once, so a page of the queue costs one read. */
  List<EvidenceFile> findByTenantIdAndValidationRequestIdIn(
      String tenantId, Collection<UUID> validationRequestIds);

  Optional<EvidenceFile> findByTenantIdAndId(String tenantId, UUID id);
}
