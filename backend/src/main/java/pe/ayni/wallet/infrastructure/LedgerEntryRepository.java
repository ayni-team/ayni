package pe.ayni.wallet.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.wallet.domain.model.LedgerEntry;
import pe.ayni.wallet.domain.model.LedgerReason;
import pe.ayni.wallet.domain.model.ReferenceType;

/**
 * The ledger. Entries are inserted and read, never changed: the entity is immutable and a trigger
 * refuses {@code UPDATE} and {@code DELETE} in the database.
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

  /**
   * Takes this university's turn to append, and holds it until the transaction ends.
   *
   * <p>This is what makes the chain a chain. Without it, two transactions appending at once read
   * the same tail, claim the same sequence number, and one of them is rejected by {@code
   * uq_ledger_entries_sequence} after having done all of its work.
   *
   * <p>Locking the last row instead is the obvious thing to do, and it does not work. PostgreSQL
   * re-checks the row a blocked transaction was waiting on, but it does not re-run the {@code order
   * by ... limit 1} that chose it, so the second writer wakes up still believing the old tail is the
   * tail. An advisory lock has no row to go stale: it is held on the university itself, so the
   * writers really do queue, and it covers the first entry of a university, when there is no row to
   * lock at all.
   *
   * <p>Two universities whose codes happen to hash alike queue behind each other. That costs a
   * little waiting and is never wrong, which is the right way round for this trade.
   */
  @Query(
      value = "select 1 from (select pg_advisory_xact_lock(hashtext(:tenantId))) as ledger_lock",
      nativeQuery = true)
  int lockLedgerOf(@Param("tenantId") String tenantId);

  /**
   * Where a university's chain currently ends.
   *
   * <p>Only ever read after {@link #lockLedgerOf(String)}, which is what keeps the answer true for
   * as long as it takes to use it. Two columns are enough: appending needs the number to continue
   * from and the hash to point at.
   */
  @Query(
      value =
          """
          select e.sequence_number as "sequenceNumber", e.entry_hash as "entryHash"
          from wallet.ledger_entries e
          where e.tenant_id = :tenantId
          order by e.sequence_number desc
          limit 1
          """,
      nativeQuery = true)
  Optional<ChainTail> chainTailOf(@Param("tenantId") String tenantId);

  /** The last link of a chain: what to continue from. */
  interface ChainTail {

    long getSequenceNumber();

    String getEntryHash();
  }

  /** Everything ever written about one thing outside wallet, such as a booking. */
  List<LedgerEntry> findByTenantIdAndReferenceTypeAndReferenceIdOrderBySequenceNumberAsc(
      String tenantId, ReferenceType referenceType, UUID referenceId);

  /**
   * The history of an account, newest first.
   *
   * <p>The period and the reasons are always given: the query service turns "no filter" into the
   * widest period and every reason, which keeps one query instead of one per combination of
   * filters.
   */
  @Query(
      """
      select entry from LedgerEntry entry
      where entry.tenantId = :tenantId
        and entry.accountId = :accountId
        and entry.occurredAt >= :from
        and entry.occurredAt < :to
        and entry.reason in :reasons
      """)
  Page<LedgerEntry> findHistory(
      @Param("tenantId") String tenantId,
      @Param("accountId") UUID accountId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("reasons") Collection<LedgerReason> reasons,
      Pageable pageable);

  /** Every entry of a university, in order, for verifying the chain. */
  List<LedgerEntry> findByTenantIdOrderBySequenceNumberAsc(String tenantId);

  List<LedgerEntry> findByTenantIdAndAccountIdOrderBySequenceNumberAsc(
      String tenantId, UUID accountId);
}
