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
 * that join items, {@link #deleteDuplicatesOf} and {@link #moveInterests}, only move or drop rows
 * and answer how many.
 */
public interface LearningInterestRepository extends JpaRepository<LearningInterest, UUID> {

  List<LearningInterest> findByTenantIdAndStudentId(String tenantId, UUID studentId);

  /**
   * Drops the interests in {@code source} of the students who already have one in {@code target}.
   * Joining two items must not leave a student interested twice in what is now one skill.
   *
   * <p>Across universities on purpose: a global tool is wanted in all of them. It returns a count
   * and never a row about a person.
   *
   * @return how many were dropped
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      delete from LearningInterest interest
      where interest.catalogItemId = :source
        and exists (
          select 1 from LearningInterest kept
          where kept.catalogItemId = :target
            and kept.tenantId = interest.tenantId
            and kept.studentId = interest.studentId)
      """)
  int deleteDuplicatesOf(@Param("source") UUID source, @Param("target") UUID target);

  /**
   * Moves the interests left in {@code source} to {@code target}. Run after {@link #deleteDuplicatesOf}.
   *
   * @return how many were moved
   */
  @Modifying(flushAutomatically = true)
  @Query("update LearningInterest interest set interest.catalogItemId = :target where interest.catalogItemId = :source")
  int moveInterests(@Param("source") UUID source, @Param("target") UUID target);
}
