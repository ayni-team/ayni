package pe.ayni.sessions.infrastructure;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.sessions.application.IssuePresenceCodesUseCase;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Sends the presence codes of the sessions that reached their fifth minute, one university at a
 * time.
 *
 * <p>Nobody makes this request, so each university is bound explicitly before anything is read, as
 * the expired holds sweep does. It runs every minute because the code is due five minutes into an
 * hour: a nightly job would be far too late. A session that fails is logged and tried again on the
 * next run; the others are not held back by it.
 */
@Component
class PresenceCheckJob {

  private static final Logger log = LoggerFactory.getLogger(PresenceCheckJob.class);

  private final IssuePresenceCodesUseCase issuePresenceCodes;
  private final IdentityApi identity;

  PresenceCheckJob(IssuePresenceCodesUseCase issuePresenceCodes, IdentityApi identity) {
    this.issuePresenceCodes = issuePresenceCodes;
    this.identity = identity;
  }

  @Scheduled(
      fixedDelayString = "${ayni.sessions.presence-check-delay:PT1M}",
      initialDelayString = "${ayni.sessions.presence-check-delay:PT1M}")
  void issueDuePresenceCodes() {
    for (String tenantId : identity.activeTenantCodes()) {
      try {
        TenantContext.runAs(tenantId, this::issueForCurrentUniversity);
      } catch (RuntimeException failure) {
        // Kept to one university: the others still get their codes.
        log.error("Could not look for due presence codes in {}", tenantId, failure);
      }
    }
  }

  private void issueForCurrentUniversity() {
    for (UUID sessionId : issuePresenceCodes.dueNow()) {
      try {
        if (issuePresenceCodes.issueFor(sessionId)) {
          log.info("Issued the presence codes of session {}", sessionId);
        }
      } catch (RuntimeException failure) {
        log.error("Could not issue the presence codes of session {}", sessionId, failure);
      }
    }
  }
}
