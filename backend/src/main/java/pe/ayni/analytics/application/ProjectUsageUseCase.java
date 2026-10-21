package pe.ayni.analytics.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.analytics.domain.model.CountedEventKind;
import pe.ayni.analytics.domain.model.UnknownUniversity;
import pe.ayni.analytics.domain.services.UniversityDay;
import pe.ayni.analytics.infrastructure.ActiveStudentDayRepository;
import pe.ayni.analytics.infrastructure.CountedEventRepository;
import pe.ayni.analytics.infrastructure.DailyIndicatorRepository;
import pe.ayni.analytics.infrastructure.UncoveredDemandRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.shared.events.DemandUncovered;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Keeps the daily indicators of each university up to date from the events the other modules
 * publish, so a coordinator's question is answered from this module's own tables.
 *
 * <p>Each method counts one kind of fact and returns whether it did: {@code false} when that fact
 * was already counted. Facts are counted once through {@code analytics.counted_events}, and a
 * student once per day through {@code analytics.active_student_days}; both are decided by a
 * primary key, so two deliveries at the same moment cannot both count. The one exception is a
 * search that found no hour: it has no identity to remember, so every one heard is counted.
 *
 * <p>The day is the one of the university's time zone, asked of {@link IdentityApi} while the
 * projection is updated and never while a coordinator reads. Every method runs in a transaction of
 * its own, so a failure rolls back only this fact and reaches the listener as an exception it can
 * swallow. Callers bind the university first.
 */
@Service
public class ProjectUsageUseCase {

  private final DailyIndicatorRepository indicators;
  private final CountedEventRepository counted;
  private final ActiveStudentDayRepository activeDays;
  private final UncoveredDemandRepository uncoveredDemand;
  private final IdentityApi identity;
  private final Clock clock;

  ProjectUsageUseCase(
      DailyIndicatorRepository indicators,
      CountedEventRepository counted,
      ActiveStudentDayRepository activeDays,
      UncoveredDemandRepository uncoveredDemand,
      IdentityApi identity,
      Clock clock) {
    this.indicators = indicators;
    this.counted = counted;
    this.activeDays = activeDays;
    this.uncoveredDemand = uncoveredDemand;
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * A session was completed: one more tutoring session, its hours, and its two participants as
   * active students of that day.
   *
   * @throws UnknownUniversity when the day cannot be told
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean sessionCompleted(SessionCompleted event) {
    if (event.sessionId() == null) {
      return false;
    }
    String tenantId = TenantContext.require();
    LocalDate day = dayOf(tenantId, event.occurredOn());
    if (counted.markCounted(tenantId, CountedEventKind.SESSION_COMPLETED.name(), event.sessionId())
        == 0) {
      return false;
    }
    // One credit is one hour.
    int hours = event.creditsEarned() == null ? 0 : event.creditsEarned().amount();
    Set<UUID> participants = new LinkedHashSet<>();
    if (event.tutorId() != null) {
      participants.add(event.tutorId());
    }
    if (event.studentId() != null) {
      participants.add(event.studentId());
    }
    int newlyActive = 0;
    for (UUID participant : participants) {
      newlyActive += activeDays.markActive(tenantId, day, participant);
    }
    indicators.add(tenantId, day, 1, hours, newlyActive, 0, 0);
    return true;
  }

  /**
   * A student entered for the first time: one more new student. Neither their email nor their code
   * is read from the event, and only the identifier is kept to tell a repeat.
   *
   * @throws UnknownUniversity when the day cannot be told
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean studentActivated(StudentActivated event) {
    if (event.userId() == null) {
      return false;
    }
    String tenantId = TenantContext.require();
    LocalDate day = dayOf(tenantId, event.occurredOn());
    if (counted.markCounted(tenantId, CountedEventKind.STUDENT_ACTIVATED.name(), event.userId())
        == 0) {
      return false;
    }
    indicators.add(tenantId, day, 0, 0, 0, 1, 0);
    return true;
  }

  /**
   * A recognition request was decided: one more recognition granted when it was approved. A
   * rejection is not an indicator of use and counts nothing.
   *
   * @throws UnknownUniversity when the day cannot be told
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean recognitionResolved(RecognitionResolved event) {
    if (!event.approved() || event.requestId() == null) {
      return false;
    }
    String tenantId = TenantContext.require();
    LocalDate day = dayOf(tenantId, event.occurredOn());
    if (counted.markCounted(tenantId, CountedEventKind.RECOGNITION_APPROVED.name(), event.requestId())
        == 0) {
      return false;
    }
    indicators.add(tenantId, day, 0, 0, 0, 0, 1);
    return true;
  }

  /**
   * A search found no hour inside its window: one more uncovered search of that course on that day.
   * A search is not a fact with an identity of its own, so nothing tells a repeated delivery from
   * another search; the counter is one atomic statement, which is what keeps two searches at the
   * same moment from losing one.
   *
   * @throws UnknownUniversity when the day cannot be told
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean demandUncovered(DemandUncovered event) {
    if (event.catalogItemId() == null) {
      return false;
    }
    String tenantId = TenantContext.require();
    LocalDate day = dayOf(tenantId, event.occurredOn());
    uncoveredDemand.countOneMore(tenantId, event.catalogItemId(), day);
    return true;
  }

  private LocalDate dayOf(String tenantId, Instant occurredOn) {
    return UniversityDay.of(occurredOn == null ? clock.instant() : occurredOn, zoneOf(tenantId));
  }

  private ZoneId zoneOf(String tenantId) {
    TenantView university;
    try {
      university = identity.requireTenant(tenantId);
    } catch (NoSuchElementException unknown) {
      throw new UnknownUniversity();
    }
    if (university == null || university.timezone() == null || university.timezone().isBlank()) {
      throw new UnknownUniversity();
    }
    return ZoneId.of(university.timezone());
  }
}
