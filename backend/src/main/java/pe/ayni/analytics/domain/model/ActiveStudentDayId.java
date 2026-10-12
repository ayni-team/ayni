package pe.ayni.analytics.domain.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Key of {@link ActiveStudentDay}. */
public class ActiveStudentDayId implements Serializable {

  private String tenantId;
  private LocalDate day;
  private UUID studentId;

  public ActiveStudentDayId() {}

  public ActiveStudentDayId(String tenantId, LocalDate day, UUID studentId) {
    this.tenantId = tenantId;
    this.day = day;
    this.studentId = studentId;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof ActiveStudentDayId other)) {
      return false;
    }
    return Objects.equals(tenantId, other.tenantId)
        && Objects.equals(day, other.day)
        && Objects.equals(studentId, other.studentId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, day, studentId);
  }
}
