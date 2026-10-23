package pe.ayni.audit.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.audit.infrastructure.ActivityLogRepository;
import pe.ayni.audit.infrastructure.ActivityLogRepository.ChainTail;

/**
 * Adds entries to the end of a university's activity log.
 *
 * <p>Every entry goes through here, whoever writes it: the listeners through {@link
 * RecordActivityUseCase}, and the use cases that write one of their own. That is what keeps the
 * chain a chain: each entry takes the next place and the hash of the entry before it, and both
 * depend on adding one entry at a time, which is why the first thing this does is take the
 * university's turn.
 *
 * <p>It has no transaction of its own on purpose and refuses to run without one. It joins the
 * transaction of whoever writes the entry, so the turn is held until the entry is committed or
 * rolled back; without a transaction the turn would be given back before the entry is written, and
 * two entries could take the same place.
 */
@Component
class ActivityLogWriter {

  private final ActivityLogRepository log;

  ActivityLogWriter(ActivityLogRepository log) {
    this.log = log;
  }

  /** Writes one entry down, at the end of the chain of its university. */
  @Transactional(propagation = Propagation.MANDATORY)
  ActivityEntry append(ActivityEntry entry) {
    String tenantId = entry.getTenantId();

    // Two entries of the same university at once queue here instead of both reading the same end
    // of the chain and claiming the same place.
    log.lockChainOf(tenantId);

    ChainTail tail = log.chainTailOf(tenantId).orElse(null);
    entry.chainAfter(
        tail == null ? 0 : tail.getSequenceNumber(), tail == null ? null : tail.getEntryHash());
    return log.save(entry);
  }

  /** Whether an entry with this identifier is already in the log. */
  @Transactional(propagation = Propagation.MANDATORY)
  boolean contains(UUID id) {
    return log.existsById(id);
  }
}
