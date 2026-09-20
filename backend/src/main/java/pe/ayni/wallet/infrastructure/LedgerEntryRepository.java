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
   * Where a university's chain currently ends, locked until the transaction ends.
   *
   * <p>Locking it is what makes the chain a chain. Two transactions appending at once would
   * otherwise read the same previous hash and claim the same sequence number, and one of them would
   * be rejected by {@code uq_ledger_entries_sequence} after doing all its work. The lock makes them
   * queue instead, and the constraint stays as the guarantee of last resort, including for the very
   * first entry of a university, when there is no row yet to lock.
   *
   * <p>The lock is written in SQL rather than asked for with {@code @Lock}, because an entry is
   * mapped as immutable and Hibernate refuses to lock what it believes can never change. Two
   * columns are enough here: appending needs the number to continue from and the hash to point at.
   */
  @Query(
      value =
          """
          select e.sequence_number as "sequenceNumber", e.entry_hash as "entryHash"
          from wallet.ledger_entries e
          where e.tenant_id = :tenantId
          order by e.sequence_number desc
          limit 1
          for update
          """,
      nativeQuery = true)
  Optional<ChainTail> lockChainTail(@Param("tenantId") String tenantId);

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
