package pe.ayni.audit.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.application.AnomalyDetail.Person;
import pe.ayni.audit.application.AnomalyDetail.Resolution;
import pe.ayni.audit.application.AnomalyDetail.Session;
import pe.ayni.audit.domain.model.Anomaly;
import pe.ayni.audit.domain.model.AnomalyNotFound;
import pe.ayni.audit.domain.model.AnomalySession;
import pe.ayni.audit.domain.model.AuditAccessDenied;
import pe.ayni.audit.infrastructure.AnomalyRepository;
import pe.ayni.audit.infrastructure.AnomalySessionRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.sessions.SessionView;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US47: a coordinator opens an alert and sees what supports it.
 *
 * <p>An anomaly of another university is not found, as if it did not exist: it is never refused.
 */
@Service
public class ReadAnomalyUseCase {

  private final CoordinatorGuard guard;
  private final AnomalyRepository anomalies;
  private final AnomalySessionRepository supportingSessions;
  private final IdentityApi identity;
  private final SessionsApi sessions;

  ReadAnomalyUseCase(
      CoordinatorGuard guard,
      AnomalyRepository anomalies,
      AnomalySessionRepository supportingSessions,
      IdentityApi identity,
      SessionsApi sessions) {
    this.guard = guard;
    this.anomalies = anomalies;
    this.supportingSessions = supportingSessions;
    this.identity = identity;
    this.sessions = sessions;
  }

  /**
   * @throws AuditAccessDenied when they are not an active coordinator of the current university
   * @throws AnomalyNotFound when the anomaly does not exist in the current university
   */
  @Transactional(readOnly = true)
  public AnomalyDetail execute(UUID coordinatorId, UUID anomalyId) {
    String tenantId = TenantContext.require();
    guard.require(coordinatorId);
    Anomaly anomaly =
        anomalies.findByTenantIdAndId(tenantId, anomalyId).orElseThrow(AnomalyNotFound::new);
    List<UUID> sessionIds =
        supportingSessions.findByTenantIdAndAnomalyId(tenantId, anomalyId).stream()
            .map(AnomalySession::getSessionId)
            .sorted(Comparator.naturalOrder())
            .toList();
    return of(anomaly, people(anomaly), sessions(sessionIds));
  }

  /** The anomaly as a coordinator reads it, with the people and the sessions already resolved. */
  static AnomalyDetail of(Anomaly anomaly, List<Person> people, List<Session> sessions) {
    Resolution resolution =
        anomaly.getResolvedAt() == null
            ? null
            : new Resolution(
                anomaly.getResolvedBy(), anomaly.getResolvedAt(), anomaly.getResolutionReason());
    return new AnomalyDetail(
        anomaly.getId(),
        anomaly.getKind().name(),
        anomaly.getSeverity().name(),
        anomaly.getStatus().name(),
        anomaly.description(),
        anomaly.getDetectedAt(),
        anomaly.getDetail(),
        people,
        sessions,
        resolution);
  }

  private List<Person> people(Anomaly anomaly) {
    List<Person> people = new ArrayList<>();
    for (Map.Entry<UUID, String> person : anomaly.involvedPeople().entrySet()) {
      String name;
      try {
        name = identity.requireUser(person.getKey()).fullName();
      } catch (NoSuchElementException unknown) {
        name = null;
      }
      people.add(new Person(person.getKey(), person.getValue(), name));
    }
    return people;
  }

  private List<Session> sessions(List<UUID> sessionIds) {
    List<Session> found = new ArrayList<>();
    for (UUID sessionId : sessionIds) {
      try {
        SessionView session = this.sessions.requireSession(sessionId);
        found.add(
            new Session(
                sessionId,
                session.scheduledStart(),
                session.scheduledEnd(),
                session.status().name()));
      } catch (NoSuchElementException unknown) {
        found.add(new Session(sessionId, null, null, null));
      }
    }
    return found;
  }
}
