package pe.ayni.recognition.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.recognition.domain.model.RecognitionProgress;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US27: how many hours a student has taught, how many their university asks for and how many are
 * missing.
 *
 * <p>The hours come from the verified sessions the student taught, read from sessions at the moment
 * of asking. Nothing is kept in between, so a session that just completed is counted on the next
 * read: there is no figure that could be late. Credits the university assigned and credits the
 * student bought are not counted, since no session stands behind them.
 */
@Service
public class ProgressQuery {

  private final IdentityApi identity;
  private final SessionsApi sessions;
  private final RecognitionRuleRepository rules;
  private final Clock clock;

  ProgressQuery(
      IdentityApi identity, SessionsApi sessions, RecognitionRuleRepository rules, Clock clock) {
    this.identity = identity;
    this.sessions = sessions;
    this.rules = rules;
    this.clock = clock;
  }

  /**
   * @throws java.util.NoSuchElementException when the person is not a user of this university
   */
  @Transactional(readOnly = true)
  public RecognitionProgress of(UUID studentId) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    identity.requireUser(studentId);
    String tenantId = TenantContext.require();

    List<SessionSummary> taught = sessions.completedSessionsOf(studentId);
    int hours = taught.stream().mapToInt(SessionSummary::hours).sum();
    Integer required =
        rules
            .findInForce(tenantId, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))
            .map(RecognitionRule::getMinimumHours)
            .orElse(null);
    return new RecognitionProgress(hours, taught.size(), required);
  }
}
