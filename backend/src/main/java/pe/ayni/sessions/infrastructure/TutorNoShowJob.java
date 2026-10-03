package pe.ayni.sessions.infrastructure;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.sessions.application.AbandonTutorNoShowUseCase;
import pe.ayni.shared.tenancy.TenantContext;

/** Finds tutor no-shows every minute, independently for each university. */
@Component
class TutorNoShowJob {

  private static final Logger log = LoggerFactory.getLogger(TutorNoShowJob.class);

  private final AbandonTutorNoShowUseCase abandonNoShows;
  private final IdentityApi identity;

  TutorNoShowJob(AbandonTutorNoShowUseCase abandonNoShows, IdentityApi identity) {
    this.abandonNoShows = abandonNoShows;
    this.identity = identity;
  }

  @Scheduled(
      fixedDelayString = "${ayni.sessions.no-show-delay:PT1M}",
      initialDelayString = "${ayni.sessions.no-show-delay:PT1M}")
  void abandonTutorNoShows() {
    for (String tenantId : identity.activeTenantCodes()) {
      try {
        TenantContext.runAs(tenantId, this::abandonForCurrentUniversity);
      } catch (RuntimeException failure) {
        log.error("Could not look for tutor no-shows in {}", tenantId, failure);
      }
    }
  }

  private void abandonForCurrentUniversity() {
    for (UUID sessionId : abandonNoShows.dueNow()) {
      try {
        if (abandonNoShows.abandonFor(sessionId)) {
          log.info("Abandoned session {} after tutor no-show", sessionId);
        }
      } catch (RuntimeException failure) {
        log.error("Could not abandon session {} after tutor no-show", sessionId, failure);
      }
    }
  }
}
