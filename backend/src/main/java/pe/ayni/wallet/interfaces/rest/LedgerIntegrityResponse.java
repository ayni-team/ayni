package pe.ayni.wallet.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.wallet.application.LedgerIntegrityReport;

/**
 * Whether the ledger of the university is still the one the application wrote.
 *
 * <p>It names the entry where the chain breaks and how many accounts disagree with their balance,
 * and nothing else: no amount, no student.
 *
 * @param intact whether the whole chain holds
 * @param entriesChecked how many entries were read, up to the broken one when there is one
 * @param checkedAt when the check ran
 * @param firstInconsistentEntryId present only when the chain does not hold
 * @param firstInconsistentPosition its place in the chain, counting from one; present only when
 *     the chain does not hold
 * @param firstInconsistentSequenceNumber the sequence number it carries; present only when the
 *     chain does not hold
 * @param balanceMismatches how many accounts have a history that does not add up to their balance
 */
@Schema(name = "LedgerIntegrity", description = "Result of verifying the ledger chain")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LedgerIntegrityResponse(
    @Schema(example = "true") boolean intact,
    @Schema(example = "1284") long entriesChecked,
    @Schema(example = "2026-10-21T08:00:00Z") Instant checkedAt,
    @Schema(example = "6b1f3c0e-7a52-4c1d-9a55-0f8d6a2b9c11") UUID firstInconsistentEntryId,
    @Schema(example = "97") Long firstInconsistentPosition,
    @Schema(example = "98") Long firstInconsistentSequenceNumber,
    @Schema(example = "0") long balanceMismatches) {

  static LedgerIntegrityResponse of(LedgerIntegrityReport report) {
    return new LedgerIntegrityResponse(
        report.intact(),
        report.entriesChecked(),
        report.checkedAt(),
        report.firstInconsistentEntryId(),
        report.firstInconsistentPosition(),
        report.firstInconsistentSequenceNumber(),
        report.balanceMismatches());
  }
}
