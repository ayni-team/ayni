package pe.ayni.recognition.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestStatus;

public interface RecognitionRequestRepository extends JpaRepository<RecognitionRequest, UUID> {

  /** The requests of the university in these states, the one that waited longest first. */
  Page<RecognitionRequest> findByTenantIdAndStatusInOrderBySubmittedAtAsc(
      String tenantId, Collection<RequestStatus> statuses, Pageable pageable);

  /** A request of the university, which is how a coordinator never reaches another one's. */
  Optional<RecognitionRequest> findByIdAndTenantId(UUID id, String tenantId);

  /**
   * A request of the university, locked for writing until the transaction ends. Whoever decides takes
   * this lock first, so two coordinators deciding at once do not both succeed: the second one finds it
   * already decided.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select request from RecognitionRequest request where request.id = :id and request.tenantId = :tenantId")
  Optional<RecognitionRequest> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") String tenantId);

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
