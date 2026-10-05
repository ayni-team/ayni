package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.skills.domain.model.EvidenceFile;

/** Every method takes the university: the files of a request are read with the request's tenant. */
public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, UUID> {

  List<EvidenceFile> findByTenantIdAndValidationRequestId(String tenantId, UUID validationRequestId);
}
