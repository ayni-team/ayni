package pe.ayni.wallet.application;

import java.time.Instant;
import java.util.UUID;

/**
 * What checking a university's ledger found. It carries no amount and no person.
 *
 * @param intact whether every entry matches its own hash and points at the one before it
 * @param entriesChecked how many entries were read; when the chain fails, the ones up to and
 *     including the first inconsistent one
 * @param checkedAt when the check ran
 * @param firstInconsistentEntryId the first entry that does not add up, or {@code null} when the
 *     chain is intact
 * @param firstInconsistentPosition its place in the chain as it was read, counting from one, or
 *     {@code null} when the chain is intact
 * @param firstInconsistentSequenceNumber the sequence number it carries, which differs from the
 *     position when an entry before it was removed, or {@code null} when the chain is intact
 * @param balanceMismatches how many accounts have a history that does not add up to their balance
 */
public record LedgerIntegrityReport(
    boolean intact,
    long entriesChecked,
    Instant checkedAt,
    UUID firstInconsistentEntryId,
    Long firstInconsistentPosition,
    Long firstInconsistentSequenceNumber,
    long balanceMismatches) {}
