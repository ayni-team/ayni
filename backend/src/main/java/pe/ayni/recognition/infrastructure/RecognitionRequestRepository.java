package pe.ayni.recognition.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.recognition.domain.model.RecognitionRequest;

public interface RecognitionRequestRepository extends JpaRepository<RecognitionRequest, UUID> {

  /** A student's requests, the latest first. */
  List<RecognitionRequest> findByTenantIdAndStudentIdOrderBySubmittedAtDesc(String tenantId, UUID studentId);

  /**
   * Waits for its turn to submit a request for this student, until the transaction ends.
   *
   * <p>A request is made of the sessions the student has not used yet. Two requests submitted at once
   * would each read the same free sessions, and the database would refuse the second as a duplicate
   * instead of telling the student how many hours they have left. Whoever submits takes this lock
   * first, so the second one finds the sessions already used.
   *
   * <p>The lock is taken from the database, so it also holds across several instances of the
   * application. It is per student: nobody else waits.
   *
   * @return nothing useful: only the wait matters
   */
  @Query(
      value = "select cast(pg_advisory_xact_lock(7050001, hashtext(:tenantId || ':' || cast(:studentId as text))) as text)",
      nativeQuery = true)
  String lockStudent(@Param("tenantId") String tenantId, @Param("studentId") UUID studentId);
}
