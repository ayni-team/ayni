package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.skills.domain.model.LearningInterest;

/**
 * Every method takes the university: no query here may cross into another one's students. The two
 * that join items, {@link #deleteDuplicatesOf} and {@link #moveInterests}, only move or drop the
 * rows of that university and answer how many.
 */
public interface LearningInterestRepository extends JpaRepository<LearningInterest, UUID> {

  List<LearningInterest> findByTenantIdAndStudentId(String tenantId, UUID studentId);

  /**
   * Waits for its turn to change the interests of one student, until the transaction ends.
   *
   * <p>Declaring an interest is reading which ones the student has and adding the rest, two steps.
   * Two requests of the same student at once, a double click on the last step of the onboarding or
   * on saving the interests, would each find an item missing and each add it, and the unique key
   * would answer the second with an error. Whoever changes them takes this lock first, so the second
   * one finds what the first one saved. There is no row of the student in this module to lock, so
   * the lock is taken from the database, which also holds across several instances. It is per
   * student: nobody else waits.
   *
   * @return nothing useful: only the wait matters
   */
  @Query(
      value =
          "select cast(pg_advisory_xact_lock(7043003, hashtext(:tenantId || ':' || cast(:studentId as text))) as text)",
      nativeQuery = true)
  String lockInterestsOf(@Param("tenantId") String tenantId, @Param("studentId") UUID studentId);

  /**
   * Drops the interests of the university in {@code source} of the students who already have one in
   * {@code target}. Joining two items must not leave a student interested twice in what is now one
   * skill.
   *
   * @return how many were dropped
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      delete from LearningInterest interest
      where interest.tenantId = :tenantId
        and interest.catalogItemId = :source
        and exists (
          select 1 from LearningInterest kept
          where kept.tenantId = :tenantId
            and kept.catalogItemId = :target
            and kept.studentId = interest.studentId)
      """)
  int deleteDuplicatesOf(
      @Param("tenantId") String tenantId, @Param("source") UUID source, @Param("target") UUID target);

  /**
   * Moves the interests of the university left in {@code source} to {@code target}. Run after {@link #deleteDuplicatesOf}.
   *
   * @return how many were moved
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update LearningInterest interest set interest.catalogItemId = :target
      where interest.tenantId = :tenantId and interest.catalogItemId = :source
      """)
  int moveInterests(
      @Param("tenantId") String tenantId, @Param("source") UUID source, @Param("target") UUID target);
}
