package pe.ayni.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delivers again what a listener did not complete (TS04).
 *
 * <p>A publication still in {@code public.event_publication} a minute after it was stored is a
 * delivery that failed, or that a stopped process never made: listeners run right after the
 * publisher commits, so a minute is far more than any of them takes. Younger ones are left alone,
 * so a delivery that is still running is not started a second time.
 *
 * <p>Each publication is handed back to its own listener, one at a time, and the listener opens its
 * own transaction and binds the university of its event with {@code TenantContext.runAs}, as it
 * does the first time. That is why this job does not loop over the universities like the others:
 * the registry belongs to none, a platform administrator's access link has none, and a failure is
 * kept to its publication, which stays for the next run.
 *
 * <p>Delivering again is only safe because every listener is idempotent: the same event delivered
 * twice changes nothing the second time. See the backend guide, listeners and jobs.
 */
@Component
class EventPublicationResubmission {

  /** How old an incomplete publication is before it is taken as failed rather than running. */
  static final Duration FAILED_AFTER = Duration.ofMinutes(1);

  private static final Logger log = LoggerFactory.getLogger(EventPublicationResubmission.class);

  private final IncompleteEventPublications publications;

  EventPublicationResubmission(IncompleteEventPublications publications) {
    this.publications = publications;
  }

  @Scheduled(
      fixedDelayString = "${ayni.events.resubmit-delay:PT5M}",
      initialDelayString = "${ayni.events.resubmit-delay:PT5M}")
  void resubmitIncompletePublications() {
    try {
      publications.resubmitIncompletePublicationsOlderThan(FAILED_AFTER);
    } catch (RuntimeException failure) {
      // The registry could not be read at all. The publications stay, and the next run tries.
      log.error("Could not resubmit the incomplete event publications", failure);
    }
  }
}
