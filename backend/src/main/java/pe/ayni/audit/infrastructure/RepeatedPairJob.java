package pe.ayni.audit.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.audit.application.DetectRepeatedPairsUseCase;
import pe.ayni.audit.application.DetectRepeatedPairsUseCase.Pair;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Looks for pairs of tutor and student that meet more than is usual, one university at a time.
 *
 * <p>Each pair is decided in a transaction of its own and a failure is logged and left behind, so
 * one pair that cannot be written never holds back the others nor the rest of the universities.
 * Its delay is {@code ayni.audit.analysis-delay}, which the tests push out of reach and which they
 * never wait for: they call {@link DetectRepeatedPairsUseCase}, as this job does.
 */
@Component
class RepeatedPairJob {

  private static final Logger log = LoggerFactory.getLogger(RepeatedPairJob.class);

  private final DetectRepeatedPairsUseCase detect;
  private final IdentityApi identity;

  RepeatedPairJob(DetectRepeatedPairsUseCase detect, IdentityApi identity) {
    this.detect = detect;
    this.identity = identity;
  }

  @Scheduled(
      fixedDelayString = "${ayni.audit.analysis-delay:PT1H}",
      initialDelayString = "${ayni.audit.analysis-delay:PT1H}")
  void analyse() {
    for (String tenantId : identity.activeTenantCodes()) {
      try {
        TenantContext.runAs(tenantId, this::analyseCurrentUniversity);
      } catch (RuntimeException failure) {
        log.error("Could not look for repeated pairs in {}", tenantId, failure);
      }
    }
  }

  private void analyseCurrentUniversity() {
    for (Pair pair : detect.candidates()) {
      try {
        if (detect.detect(pair)) {
          log.info("Repeated pair raised for tutor {}", pair.tutorId());
        }
      } catch (RuntimeException failure) {
        log.error("Could not decide the pair of tutor {}", pair.tutorId(), failure);
      }
    }
  }
}
