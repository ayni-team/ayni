package pe.ayni.skills.application;

import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.EvidenceFile;
import pe.ayni.skills.infrastructure.EvidenceFileRepository;

/**
 * US16: a coordinator opens a file a student attached, to review it.
 *
 * <p>Without this the queue would list evidence nobody can look at. The file is found through the
 * request it came with, and a file of another request, or of another university, is not found: the
 * identifier of a file alone opens nothing.
 */
@Service
public class OpenEvidenceFileUseCase {

  private final CoordinatorGuard coordinators;
  private final EvidenceFileRepository evidenceFiles;
  private final EvidenceStoragePort storage;

  OpenEvidenceFileUseCase(
      CoordinatorGuard coordinators, EvidenceFileRepository evidenceFiles, EvidenceStoragePort storage) {
    this.coordinators = coordinators;
    this.evidenceFiles = evidenceFiles;
    this.storage = storage;
  }

  /** A file and its content. The caller closes the stream. */
  public record OpenedEvidence(EvidenceFile file, InputStream content) {}

  /**
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not a coordinator
   * @throws NoSuchElementException when the file is not one of that request in this university, or
   *     its content is gone from the storage
   * @throws EvidenceStorageException when the content exists but cannot be read
   */
  @Transactional(readOnly = true)
  public OpenedEvidence execute(UUID coordinatorId, UUID requestId, UUID fileId) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    Objects.requireNonNull(requestId, "requestId must not be null");
    Objects.requireNonNull(fileId, "fileId must not be null");

    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    EvidenceFile file =
        evidenceFiles
            .findByTenantIdAndId(tenantId, fileId)
            .filter(found -> found.getValidationRequestId().equals(requestId))
            .orElseThrow(() -> new NoSuchElementException("evidence file %s not found".formatted(fileId)));

    return new OpenedEvidence(file, storage.open(tenantId, file.getStorageKey()));
  }
}
