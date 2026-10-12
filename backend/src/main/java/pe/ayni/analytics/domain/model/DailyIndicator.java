package pe.ayni.analytics.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/**
 * What a university did on one day, in numbers: the row analytics keeps instead of asking the
 * other modules.
 *
 * <p>The projection writes these rows with an atomic increment, so two events arriving at once
 * cannot lose one; the entity is only ever read, and Hibernate treats it as immutable. It holds
 * counts and never a person.
 */
@Entity
@Immutable
@IdClass(DailyIndicatorId.class)
@Table(schema = "analytics", name = "daily_indicators")
public class DailyIndicator {

  @Id
  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Id
  @Column(name = "day", nullable = false, updatable = false)
  private LocalDate day;

  @Column(name = "sessions_completed", nullable = false, updatable = false)
  private int sessionsCompleted;

  @Column(name = "hours_exchanged", nullable = false, updatable = false)
  private int hoursExchanged;

  @Column(name = "active_students", nullable = false, updatable = false)
  private int activeStudents;

  @Column(name = "new_students", nullable = false, updatable = false)
  private int newStudents;

  @Column(name = "requests_approved", nullable = false, updatable = false)
  private int requestsApproved;

  protected DailyIndicator() {
    // Required by JPA
  }

  public String getTenantId() {
    return tenantId;
  }

  public LocalDate getDay() {
    return day;
  }

  public int getSessionsCompleted() {
    return sessionsCompleted;
  }

  public int getHoursExchanged() {
    return hoursExchanged;
  }

  public int getActiveStudents() {
    return activeStudents;
  }

  public int getNewStudents() {
    return newStudents;
  }

  public int getRequestsApproved() {
    return requestsApproved;
  }
}
