package pe.ayni.audit.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.domain.model.Anomaly;
import pe.ayni.audit.domain.model.AnomalyKind;
import pe.ayni.audit.domain.model.AnomalySession;
import pe.ayni.audit.domain.services.RepeatedPairRule;
import pe.ayni.audit.infrastructure.AnomalyRepository;
import pe.ayni.audit.infrastructure.AnomalySessionRepository;
import pe.ayni.audit.infrastructure.CompletedSessionsRepository;
import pe.ayni.audit.infrastructure.CompletedSessionsRepository.PairCount;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Looks for a tutor and a student who completed more sessions together than is usual in a period.
 *
 * <p>It reads what the activity log already recorded, so it asks no other module. The job that
 * calls it lists the candidates of one university and then decides each pair in a transaction of
 * its own: a pair that cannot be written does not hold back the rest.
 *
 * <p>A pair is pointed out once per period. While an alert about it, open or dismissed, is younger
 * than the period it is not raised again, so a coordinator who dismissed it is not asked again the
 * next time the job runs.
 */
@Service
public class DetectRepeatedPairsUseCase {

  /** A tutor and a student who have to be looked at. */
  public record Pair(UUID tutorId, UUID studentId) {}

  private final CompletedSessionsRepository completed;
  private final AnomalyRepository anomalies;
  private final AnomalySessionRepository supportingSessions;
  private final Clock clock;
  private final RepeatedPairRule rule;
  private final Duration period;

  DetectRepeatedPairsUseCase(
      CompletedSessionsRepository completed,
      AnomalyRepository anomalies,
      AnomalySessionRepository supportingSessions,
      Clock clock,
      @Value("${ayni.audit.repeated-pair-threshold:3}") int threshold,
      @Value("${ayni.audit.repeated-pair-period:P30D}") Duration period) {
    if (period == null || period.compareTo(Duration.ofDays(1)) < 0) {
      throw new IllegalArgumentException("repeated-pair-period must be at least one day");
    }
    this.completed = completed;
    this.anomalies = anomalies;
    this.supportingSessions = supportingSessions;
    this.clock = clock;
    this.rule = new RepeatedPairRule(threshold);
    this.period = period;
  }

  /** The pairs of the current university that completed more sessions than usual in the period. */
  @Transactional(readOnly = true)
  public List<Pair> candidates() {
    Instant now = clock.instant();
    List<Pair> pairs = new ArrayList<>();
    for (PairCount found :
        completed.pairsAbove(TenantContext.require(), now.minus(period), now, rule.threshold())) {
      try {
        pairs.add(new Pair(UUID.fromString(found.getTutorId()), UUID.fromString(found.getStudentId())));
      } catch (IllegalArgumentException notAnId) {
        // An entry whose people are not identifiers cannot be about anybody.
      }
    }
    return pairs;
  }

  /**
   * Decides one pair, in a transaction of its own.
   *
   * @return whether an alert was raised; {@code false} when the pair is within what is usual or was
   *     already pointed out in this period
   */
  @Transactional
  public boolean detect(Pair pair) {
    String tenantId = TenantContext.require();
    Instant now = clock.instant();
    Instant from = now.minus(period);

    // Two runs at once look at the same pair one after the other, and the second one finds the
    // alert of the first.
    anomalies.lockPair(tenantId + ":" + pair.tutorId() + ":" + pair.studentId());
    if (anomalies.pairPointedOutSince(
        tenantId, pair.tutorId(), pair.studentId().toString(), from)) {
      return false;
    }
    List<UUID> sessionIds =
        completed.sessionsOf(
            tenantId, pair.tutorId().toString(), pair.studentId().toString(), from, now);
    if (!rule.exceeds(sessionIds.size())) {
      return false;
    }

    Map<String, String> detail = new LinkedHashMap<>();
    detail.put("tutorId", pair.tutorId().toString());
    detail.put("studentId", pair.studentId().toString());
    detail.put("sessions", Integer.toString(sessionIds.size()));
    detail.put("threshold", Integer.toString(rule.threshold()));
    detail.put("periodDays", Long.toString(period.toDays()));
    detail.put("from", from.toString());
    detail.put("to", now.toString());

    UUID id = UUID.randomUUID();
    anomalies.save(
        Anomaly.detected(
            id,
            tenantId,
            AnomalyKind.REPEATED_PAIR,
            pair.tutorId(),
            rule.severityOf(sessionIds.size()),
            detail,
            now));
    supportingSessions.saveAll(
        sessionIds.stream().map(session -> new AnomalySession(id, session, tenantId)).toList());
    return true;
  }
}
