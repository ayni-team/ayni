package pe.ayni.audit.application;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.domain.model.Anomaly;
import pe.ayni.audit.domain.model.AnomalyKind;
import pe.ayni.audit.domain.model.AnomalySession;
import pe.ayni.audit.domain.model.Severity;
import pe.ayni.audit.domain.services.ShortSessionRule;
import pe.ayni.audit.infrastructure.AnomalyRepository;
import pe.ayni.audit.infrastructure.AnomalySessionRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Points out what a single session says about itself: that its presence could not be verified, or
 * that it lasted far less than what was booked.
 *
 * <p>Each method runs in a transaction of its own, so a failure here rolls back only the alert and
 * reaches the listener as an exception it can swallow. The alert of one kind of one session has a
 * derived identifier: the same event delivered twice finds it already there and adds nothing.
 *
 * <p>Callers bind the university first, because the real duration is asked of {@link SessionsApi}.
 */
@Service
public class DetectSessionAnomaliesUseCase {

  private static final Logger log = LoggerFactory.getLogger(DetectSessionAnomaliesUseCase.class);

  private final AnomalyRepository anomalies;
  private final AnomalySessionRepository supportingSessions;
  private final SessionsApi sessions;
  private final Clock clock;
  private final ShortSessionRule shortSession;

  DetectSessionAnomaliesUseCase(
      AnomalyRepository anomalies,
      AnomalySessionRepository supportingSessions,
      SessionsApi sessions,
      Clock clock,
      @Value("${ayni.audit.short-session-ratio:0.5}") double shortSessionRatio) {
    this.anomalies = anomalies;
    this.supportingSessions = supportingSessions;
    this.sessions = sessions;
    this.clock = clock;
    this.shortSession = new ShortSessionRule(shortSessionRatio);
  }

  /**
   * A session ended without every participant confirming they were there.
   *
   * @return whether an alert was raised; {@code false} when it already existed
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean failedPresence(SessionUnverified event) {
    if (event.sessionId() == null || event.tutorId() == null) {
      return false;
    }
    List<UUID> unconfirmed = event.unconfirmedUserIds() == null ? List.of() : event.unconfirmedUserIds();
    Map<String, String> detail = new LinkedHashMap<>();
    detail.put("tutorId", event.tutorId().toString());
    if (event.studentId() != null) {
      detail.put("studentId", event.studentId().toString());
    }
    if (!unconfirmed.isEmpty()) {
      detail.put(
          "unconfirmedUserIds",
          unconfirmed.stream().map(UUID::toString).collect(Collectors.joining(",")));
    }
    boolean everyoneFailed =
        unconfirmed.contains(event.tutorId())
            && event.studentId() != null
            && unconfirmed.contains(event.studentId());
    return raise(
        AnomalyKind.FAILED_PRESENCE,
        event.sessionId(),
        event.tutorId(),
        everyoneFailed ? Severity.HIGH : Severity.MEDIUM,
        detail);
  }

  /**
   * A session was completed. It is an anomaly when it lasted less than the configured fraction of
   * what was booked.
   *
   * @return whether an alert was raised; {@code false} when the session was not short, when
   *     sessions cannot say how long it lasted, or when the alert already existed
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean shortDuration(SessionCompleted event) {
    if (event.sessionId() == null || event.tutorId() == null) {
      return false;
    }
    SessionSummary session =
        sessions.completedSessionsOf(event.tutorId()).stream()
            .filter(summary -> event.sessionId().equals(summary.sessionId()))
            .findFirst()
            .orElse(null);
    if (session == null || session.startedAt() == null || session.endedAt() == null) {
      log.trace("No duration to judge for a completed session of university {}", event.tenantId());
      return false;
    }
    Duration booked = Duration.ofHours(session.hours());
    Duration actual = Duration.between(session.startedAt(), session.endedAt());
    if (!shortSession.isShort(booked, actual)) {
      return false;
    }
    Map<String, String> detail = new LinkedHashMap<>();
    detail.put("tutorId", event.tutorId().toString());
    if (event.studentId() != null) {
      detail.put("studentId", event.studentId().toString());
    }
    detail.put("bookedMinutes", Long.toString(booked.toMinutes()));
    detail.put("actualMinutes", Long.toString(Math.max(actual.toMinutes(), 0)));
    detail.put("ratio", Double.toString(shortSession.ratio()));
    return raise(
        AnomalyKind.SHORT_DURATION,
        event.sessionId(),
        event.tutorId(),
        shortSession.severityOf(booked, actual),
        detail);
  }

  private boolean raise(
      AnomalyKind kind, UUID sessionId, UUID subjectId, Severity severity, Map<String, String> detail) {
    String tenantId = TenantContext.require();
    UUID id = Anomaly.idFor(tenantId, kind, sessionId);
    if (anomalies.existsById(id)) {
      return false;
    }
    anomalies.save(Anomaly.detected(id, tenantId, kind, subjectId, severity, detail, clock.instant()));
    supportingSessions.save(new AnomalySession(id, sessionId, tenantId));
    return true;
  }
}
