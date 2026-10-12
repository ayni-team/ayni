package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import pe.ayni.skills.domain.model.EvidenceFile;

/** A file that came with a submission. The key it is stored under is not shown. */
@Schema(name = "EvidenceFile", description = "A file attached as evidence")
public record EvidenceFileResponse(
    @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID id,
    @Schema(example = "portfolio.pdf") String fileName,
    @Schema(example = "application/pdf") String contentType,
    @Schema(example = "204800") long sizeBytes) {

  static EvidenceFileResponse of(EvidenceFile file) {
    return new EvidenceFileResponse(
        file.getId(), file.getFileName(), file.getContentType(), file.getSizeBytes());
  }
}
