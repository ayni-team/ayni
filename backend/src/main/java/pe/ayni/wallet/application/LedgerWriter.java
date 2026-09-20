package pe.ayni.wallet.application;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.ayni.wallet.domain.model.LedgerEntry;
import pe.ayni.wallet.domain.model.Movement;
import pe.ayni.wallet.infrastructure.LedgerEntryRepository;

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
    LedgerEntry previous =
        entries.findFirstByTenantIdOrderBySequenceNumberDesc(tenantId).orElse(null);
    long nextSequence = previous == null ? 1 : previous.sequenceNumber() + 1;

    List<LedgerEntry> appended = new ArrayList<>(movements.size());
    for (Movement movement : movements) {
      LedgerEntry entry = LedgerEntry.following(previous, nextSequence++, movement, now);
      // persist, not save: an entry is only ever inserted, and merge would ask the database
      // whether this one already exists before inserting it anyway.
      entityManager.persist(entry);
      appended.add(entry);
      previous = entry;
    }
    return appended;
  }
}
