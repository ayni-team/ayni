package pe.ayni.booking.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.booking.domain.model.StudentAbsence;

/** Persists the sessions students did not come to. */
public interface StudentAbsenceRepository extends JpaRepository<StudentAbsence, UUID> {

  boolean existsByTenantIdAndBookingId(String tenantId, UUID bookingId);

  /** How many absences each of these students has, in one query. Students with none are absent. */
  @Query(
      """
      select absence.studentId as studentId, count(absence) as absences
      from StudentAbsence absence
      where absence.tenantId = :tenantId and absence.studentId in :studentIds
      group by absence.studentId
      """)
  List<AbsencesOfStudent> countByStudent(
      @Param("tenantId") String tenantId, @Param("studentIds") Collection<UUID> studentIds);

  /** The number of absences of one student. */
  interface AbsencesOfStudent {

    UUID getStudentId();

    long getAbsences();
  }
}
