package pe.ayni.wallet.application;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import pe.ayni.wallet.domain.model.LedgerEntry;
import pe.ayni.wallet.domain.model.Movement;
import pe.ayni.wallet.infrastructure.LedgerEntryRepository;
import pe.ayni.wallet.infrastructure.LedgerEntryRepository.ChainTail;

/**
 * Appends movements to a university's ledger.
 *
 * <p>Everything that moves credits goes through here, which is what keeps the two properties of the
 * ledger true: the entries of a university are numbered without gaps, and each one carries the hash
 * of the one before it.
 *
 * <p>It has no transaction of its own on purpose. It runs inside the transaction of the use case
 * that called it, so the entries and the groups they describe are written together or not at all.
 */
@Component
class LedgerWriter {

  private final LedgerEntryRepository entries;
  private final EntityManager entityManager;
  private final Clock clock;

  LedgerWriter(LedgerEntryRepository entries, EntityManager entityManager, Clock clock) {
    this.entries = entries;
    this.entityManager = entityManager;
    this.clock = clock;
  }

  /** Writes one movement down. */
  LedgerEntry append(Movement movement) {
    return append(List.of(movement)).getFirst();
  }

  /**
   * Writes several movements down, in the order they are given.
   *
   * <p>A charge that takes credits from three groups is three entries, one per group, because that
   * is what lets a refund put every credit back where it came from.
   */
  List<LedgerEntry> append(List<Movement> movements) {

    if (movements.isEmpty()) {
      return List.of();
    }

    String tenantId = movements.getFirst().tenantId();
    Instant now = clock.instant();

    // Locks the tail of this university's chain until the transaction ends, so that two
    // simultaneous charges append one after the other instead of both claiming the same place.
    Optional<LedgerEntryRepository.ChainTail> tail = entries.lockChainTail(tenantId);
    long nextSequence = tail.map(ChainTail::getSequenceNumber).orElse(0L) + 1;
    String previousHash = tail.map(ChainTail::getEntryHash).orElse(null);

    List<LedgerEntry> appended = new ArrayList<>(movements.size());
    for (Movement movement : movements) {
      LedgerEntry entry =
          LedgerEntry.followingHash(previousHash, nextSequence++, movement, now);
      // persist, not save: an entry is only ever inserted, and merge would ask the database
      // whether this one already exists before inserting it anyway.
      entityManager.persist(entry);
      appended.add(entry);
      previousHash = entry.entryHash();
    }
    return appended;
  }
}
