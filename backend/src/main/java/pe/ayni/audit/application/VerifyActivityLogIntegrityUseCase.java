package pe.ayni.audit.application;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.audit.domain.model.AuditAccessDenied;
import pe.ayni.audit.domain.services.ActivityChain;
import pe.ayni.audit.infrastructure.ActivityLogRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US48: a coordinator checks that the activity log of their university is still the one the
 * application wrote.
 *
 * <p>The chain is read a page at a time, with a cursor on the place of each entry, and only the last
 * entry of each page is kept to check the next one against. After each page the persistence context
 * is emptied, so a log of millions of entries costs the memory of one page. The entries from before
 * the chain carry no hash: they are neither read nor counted, and never reported as altered.
 *
 * <p>Only an active coordinator of the university may ask. Anybody else is refused and the refusal
 * is written down, exactly as when they ask for the log itself, which is why the transaction does
 * not roll back on {@link AuditAccessDenied}. Repairing a chain is not done here; this only finds
 * where it breaks.
 */
@Service
public class VerifyActivityLogIntegrityUseCase {

  /**
   * What checking the chain found.
   *
   * @param intact whether every entry matches its own hash and carries the hash of the one before
   * @param entriesChecked how many chained entries were read; when the chain fails, the ones up to
   *     and including the first inconsistent one
   * @param checkedAt when the check ran
   * @param firstInconsistentEntryId the first entry that does not fit, {@code null} when intact
   * @param firstInconsistentOccurredAt when that entry says its fact happened, {@code null} when
   *     intact
   * @param firstInconsistentSequenceNumber the place in the chain that entry claims, which differs
   *     from {@code entriesChecked} when an entry before it was removed; {@code null} when intact
   */
  public record Report(
      boolean intact,
      long entriesChecked,
      Instant checkedAt,
      UUID firstInconsistentEntryId,
      Instant firstInconsistentOccurredAt,
      Long firstInconsistentSequenceNumber) {}

  private final CoordinatorGuard guard;
  private final ActivityLogRepository log;
  private final ActivityLogWriter writer;
  private final EntityManager entityManager;
  private final Clock clock;
  private final int pageSize;

  VerifyActivityLogIntegrityUseCase(
      CoordinatorGuard guard,
      ActivityLogRepository log,
      ActivityLogWriter writer,
      EntityManager entityManager,
      Clock clock,
      @Value("${ayni.audit.integrity-page-size:500}") int pageSize) {
    if (pageSize < 1) {
      throw new IllegalArgumentException("ayni.audit.integrity-page-size must be at least 1");
    }
    this.guard = guard;
    this.log = log;
    this.writer = writer;
    this.entityManager = entityManager;
    this.clock = clock;
    this.pageSize = pageSize;
  }

  /**
   * @param coordinatorId who asks
   * @throws AuditAccessDenied when they are not an active coordinator of the current university
   */
  @Transactional(noRollbackFor = AuditAccessDenied.class)
  public Report execute(UUID coordinatorId) {
    String tenantId = TenantContext.require();
    Instant now = clock.instant();
    try {
      guard.require(coordinatorId);
    } catch (AuditAccessDenied denied) {
      writer.append(ConsultActivityLogUseCase.denial(tenantId, coordinatorId, denied, now));
      throw denied;
    }

    long checked = 0;
    ActivityEntry previous = null;
    long after = 0;

    while (true) {
      List<ActivityEntry> page =
          log.findByTenantIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
              tenantId, after, PageRequest.ofSize(pageSize));
      if (page.isEmpty()) {
        return new Report(true, checked, now, null, null, null);
      }

      Optional<ActivityEntry> inconsistent = ActivityChain.firstInconsistentEntry(previous, page);
      if (inconsistent.isPresent()) {
        ActivityEntry entry = inconsistent.get();
        return new Report(
            false,
            checked + page.indexOf(entry) + 1,
            now,
            entry.getId(),
            entry.getOccurredAt(),
            entry.getSequenceNumber());
      }

      checked += page.size();
      previous = page.getLast();
      after = previous.getSequenceNumber();
      // The page is checked and nothing needs it but its last entry, which stays readable once
      // detached: let the rest go before reading the next one.
      entityManager.clear();
    }
  }
}
