package pe.ayni.audit.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}
