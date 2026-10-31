package pe.ayni.reputation.infrastructure;

import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.reputation.application.RateSessionUseCase;
import pe.ayni.shared.demo.DemoRatings;

/** Reputation's part of the history of the {@code demo} profile: the use case of rating. */
@Component
@Profile("demo")
class DemoHistoryRatings implements DemoRatings {

  private final RateSessionUseCase rate;

  DemoHistoryRatings(RateSessionUseCase rate) {
    this.rate = rate;
  }

  @Override
  public void rateTutor(UUID sessionId, UUID studentId, int stars, Set<String> tags, String comment) {
    rate.rate(sessionId, studentId, stars, tags, null, null, null, comment);
  }

  @Override
  public void rateStudent(
      UUID sessionId, UUID tutorId, boolean punctual, boolean connectionOk, boolean flowed,
      String comment) {
    rate.rate(sessionId, tutorId, null, null, punctual, connectionOk, flowed, comment);
  }
}
