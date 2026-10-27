package pe.ayni.audit.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.audit.domain.model.ActivityEntry;

/**
 * The entries of the activity log.
 *
 * <p>Entries are only added and read: there is no method that changes one, and the table would
 * refuse it. Every read is built by {@link #matching}, which always starts from the university.
 */
public interface ActivityLogRepository
    extends JpaRepository<ActivityEntry, UUID>, JpaSpecificationExecutor<ActivityEntry> {

  /**
   * The entries of one university, narrowed by whatever filters are given.
   *
   * @param actorId only what this person did, or {@code null}
   * @param resourceType only entries about this kind of resource, or {@code null}
   * @param resourceId only entries about this resource, or {@code null}
   * @param from only entries at or after this moment, or {@code null}
   * @param to only entries before this moment, or {@code null}
   */
  static Specification<ActivityEntry> matching(
      String tenantId,
      UUID actorId,
      String resourceType,
      UUID resourceId,
      Instant from,
      Instant to) {
    Specification<ActivityEntry> spec = (entry, query, cb) -> cb.equal(entry.get("tenantId"), tenantId);
    if (actorId != null) {
      spec = spec.and((entry, query, cb) -> cb.equal(entry.get("actorId"), actorId));
    }
    if (resourceType != null) {
      spec = spec.and((entry, query, cb) -> cb.equal(entry.get("resourceType"), resourceType));
    }
    if (resourceId != null) {
      spec = spec.and((entry, query, cb) -> cb.equal(entry.get("resourceId"), resourceId));
    }
    if (from != null) {
      spec = spec.and((entry, query, cb) -> cb.greaterThanOrEqualTo(entry.<Instant>get("occurredAt"), from));
    }
    if (to != null) {
      spec = spec.and((entry, query, cb) -> cb.lessThan(entry.<Instant>get("occurredAt"), to));
    }
    return spec;
  }

  /**
   * Takes this university's turn to add to its chain, and holds it until the transaction ends.
   *
   * <p>Without it, two entries written at once read the same end of the chain, claim the same
   * place, and one of them is refused by {@code uq_activity_log_sequence}. Locking the last row
   * would not do, as the ledger found: a writer that waited on it wakes up still believing the old
   * end is the end, and there is no row at all for a university's first entry. The lock is held on
   * the university itself, under a key of its own (7048001, after US48), so it never waits on the
   * ledger's lock nor on any other.
   */
  @Query(
      value = "select cast(pg_advisory_xact_lock(7048001, hashtext(:tenantId)) as text)",
      nativeQuery = true)
  String lockChainOf(@Param("tenantId") String tenantId);

  /**
   * Where a university's chain ends now.
   *
   * <p>Only ever read after {@link #lockChainOf(String)}, which is what keeps the answer true for as
   * long as it takes to use it. The entries from before the chain have no place in it and are left
   * out, which also keeps them from being read as its end.
   */
  @Query(
      value =
          """
          select e.sequence_number as "sequenceNumber", e.entry_hash as "entryHash"
          from audit.activity_log e
          where e.tenant_id = :tenantId and e.sequence_number is not null
          order by e.sequence_number desc
          limit 1
          """,
      nativeQuery = true)
  Optional<ChainTail> chainTailOf(@Param("tenantId") String tenantId);

  /** The last link of a chain: what the next entry continues from. */
  interface ChainTail {

    long getSequenceNumber();

    String getEntryHash();
  }

  /**
   * The next stretch of a university's chain, after the given place.
   *
   * <p>A cursor and not an offset: the verification asks for the entries that follow the last one
   * it checked, so each page is one range read of {@code uq_activity_log_sequence} however far into
   * the chain it is, and the chain is never held whole in memory. The entries from before the chain
   * have no place and are never returned.
   */
  List<ActivityEntry> findByTenantIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
      String tenantId, long afterSequenceNumber, Pageable pageable);
}
