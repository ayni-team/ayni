package pe.ayni.audit.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.audit.domain.model.ActorRole;
import pe.ayni.audit.domain.model.AuditAccessDenied;
import pe.ayni.audit.domain.model.InvalidPeriod;
import pe.ayni.audit.infrastructure.ActivityLogRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US48: a coordinator reads what happened in their university.
 *
 * <p>Only an active coordinator of the university of the request may. Anybody else is refused, and
 * the refusal is written down as one more entry of the log: the attempt is itself something a
 * coordinator may need to account for. That is why the transaction does not roll back on {@link
 * AuditAccessDenied}: the entry is written first and the exception that follows would otherwise
 * take it away.
 */
@Service
public class ConsultActivityLogUseCase {

  /** The most entries a page may hold. */
  public static final int MOST_PER_PAGE = 100;

  /** The action of the entry that remembers a refused attempt. */
  public static final String ACCESS_DENIED = "AUDIT_LOG_ACCESS_DENIED";

  private final CoordinatorGuard guard;
  private final ActivityLogRepository log;
  private final Clock clock;

  ConsultActivityLogUseCase(CoordinatorGuard guard, ActivityLogRepository log, Clock clock) {
    this.guard = guard;
    this.log = log;
    this.clock = clock;
  }

  /** What to look for. Every field is optional. */
  public record Filter(
      UUID actorId, String resourceType, UUID resourceId, Instant from, Instant to) {}

  /** One entry as the coordinator reads it. */
  public record Item(
      UUID id,
      Instant occurredAt,
      UUID actorId,
      String actorRole,
      String action,
      String resourceType,
      UUID resourceId,
      Map<String, String> details) {}

  /** A page of entries, the most recent first. */
  public record LogPage(List<Item> items, int page, int size, long total) {}

  /**
   * @param coordinatorId who asks
   * @param page from zero; below zero it is zero
   * @param size entries per page, at least 1 and at most {@link #MOST_PER_PAGE}
   * @throws AuditAccessDenied when they are not an active coordinator of the current university
   * @throws InvalidPeriod when the period ends before it starts
   */
  @Transactional(noRollbackFor = AuditAccessDenied.class)
  public LogPage execute(UUID coordinatorId, Filter filter, int page, int size) {
    String tenantId = TenantContext.require();
    try {
      guard.require(coordinatorId);
    } catch (AuditAccessDenied denied) {
      log.save(denial(tenantId, coordinatorId, denied));
      throw denied;
    }
    if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
      throw new InvalidPeriod();
    }
    String type =
        filter.resourceType() == null || filter.resourceType().isBlank()
            ? null
            : filter.resourceType().strip().toUpperCase(Locale.ROOT);

    Page<ActivityEntry> found =
        log.findAll(
            ActivityLogRepository.matching(
                tenantId, filter.actorId(), type, filter.resourceId(), filter.from(), filter.to()),
            PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MOST_PER_PAGE),
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));

    return new LogPage(
        found.getContent().stream().map(ConsultActivityLogUseCase::item).toList(),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements());
  }

  private ActivityEntry denial(String tenantId, UUID personId, AuditAccessDenied denied) {
    ActorRole role = denied.role();
    return ActivityEntry.of(
        UUID.randomUUID(),
        tenantId,
        personId,
        role,
        ACCESS_DENIED,
        "AUDIT_LOG",
        null,
        Map.of("reason", denied.reason()),
        clock.instant());
  }

  private static Item item(ActivityEntry entry) {
    return new Item(
        entry.getId(),
        entry.getOccurredAt(),
        entry.getActorId(),
        entry.getActorRole().name(),
        entry.getAction(),
        entry.getResourceType(),
        entry.getResourceId(),
        entry.getMetadata());
  }
}
