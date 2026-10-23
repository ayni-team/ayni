package pe.ayni.audit.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.audit.application.VerifyActivityLogIntegrityUseCase.Report;

/**
 * Whether the activity log of the university is still the one the application wrote.
 *
 * <p>It names the first entry that does not fit the sequence and nothing about what it says: who
 * acted and what they did are read in the log itself.
 *
 * @param intact whether the whole chain holds
 * @param entriesChecked how many chained entries were read, up to the inconsistent one when there
 *     is one
 * @param checkedAt when the check ran
 * @param firstInconsistentEntryId present only when the chain does not hold
 * @param firstInconsistentOccurredAt present only when the chain does not hold
 * @param firstInconsistentSequenceNumber the place in the chain the entry claims; present only when
 *     the chain does not hold
 */
@Schema(name = "ActivityLogIntegrity", description = "Result of verifying the chain of the log")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivityLogIntegrityResponse(
    @Schema(example = "true") boolean intact,
    @Schema(example = "1284") long entriesChecked,
    @Schema(example = "2026-10-21T08:00:00Z") Instant checkedAt,
    @Schema(example = "6b1f3c0e-7a52-4c1d-9a55-0f8d6a2b9c11") UUID firstInconsistentEntryId,
    @Schema(example = "2026-10-14T16:20:00Z") Instant firstInconsistentOccurredAt,
    @Schema(example = "97") Long firstInconsistentSequenceNumber) {

  static ActivityLogIntegrityResponse of(Report report) {
    return new ActivityLogIntegrityResponse(
        report.intact(),
        report.entriesChecked(),
        report.checkedAt(),
        report.firstInconsistentEntryId(),
        report.firstInconsistentOccurredAt(),
        report.firstInconsistentSequenceNumber());
  }
}
