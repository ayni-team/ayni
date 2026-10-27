package pe.ayni.sessions.infrastructure;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.sessions.application.CloseOverdueSessionsUseCase;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Closes the sessions nobody closed, fifteen minutes after their booked hour, one university at a
 * time.
 *
 * <p>Runs every minute, like the presence codes: the tutor is paid when the session closes, and
 * waiting for a nightly run would leave them unpaid for a day. A session that fails is logged and
 * tried again on the next run; the others are not held back by it.
 */
@Component
class SessionClosingJob {

  private static final Logger log = LoggerFactory.getLogger(SessionClosingJob.class);

  private final CloseOverdueSessionsUseCase closeOverdueSessions;
  private final IdentityApi identity;

  SessionClosingJob(CloseOverdueSessionsUseCase closeOverdueSessions, IdentityApi identity) {
    this.closeOverdueSessions = closeOverdueSessions;
    this.identity = identity;
  }

  @Scheduled(
      fixedDelayString = "${ayni.sessions.closing-delay:PT1M}",
      initialDelayString = "${ayni.sessions.closing-delay:PT1M}")
  void closeOverdueSessions() {
    for (String tenantId : identity.activeTenantCodes()) {
      try {
        TenantContext.runAs(tenantId, this::closeForCurrentUniversity);
      } catch (RuntimeException failure) {
        // Kept to one university: the others still get their sessions closed.
        log.error("Could not look for overdue sessions in {}", tenantId, failure);
      }
    }
  }

  private void closeForCurrentUniversity() {
    for (UUID sessionId : closeOverdueSessions.dueNow()) {
      try {
        if (closeOverdueSessions.closeFor(sessionId)) {
          log.info("Closed overdue session {}", sessionId);
        }
      } catch (RuntimeException failure) {
        log.error("Could not close session {}", sessionId, failure);
      }
    }
  }
}
