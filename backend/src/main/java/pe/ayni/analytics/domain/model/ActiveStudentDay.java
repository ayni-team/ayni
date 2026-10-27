package pe.ayni.analytics.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * A student who took part in a session on a day.
 *
 * <p>It is what makes a student count once a day however many sessions they had, and what lets a
 * period count distinct people instead of adding up its days. Only identifiers, never returned.
 */
@Entity
@Immutable
@IdClass(ActiveStudentDayId.class)
@Table(schema = "analytics", name = "active_student_days")
public class ActiveStudentDay {

  @Id
  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Id
  @Column(name = "day", nullable = false, updatable = false)
  private LocalDate day;

  @Id
  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  protected ActiveStudentDay() {
    // Required by JPA
  }
}
