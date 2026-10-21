package pe.ayni.recognition.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.recognition.domain.model.RecognitionRule;

public interface RecognitionRuleRepository extends JpaRepository<RecognitionRule, UUID> {

  /**
   * The rule of the university in force on a day: the latest one that has started and was not
   * replaced.
   */
  @Query(
      """
      select rule from RecognitionRule rule
      where rule.tenantId = :tenantId
        and rule.supersededAt is null
        and rule.validFrom <= :day
      order by rule.validFrom desc, rule.createdAt desc
      limit 1
      """)
  Optional<RecognitionRule> findInForce(@Param("tenantId") String tenantId, @Param("day") LocalDate day);

  /** The rules of the university that no later one has replaced, whether they started or not. */
  List<RecognitionRule> findByTenantIdAndSupersededAtIsNull(String tenantId);

  /**
   * Waits for its turn to change the rule of this university, until the transaction ends.
   *
   * <p>Setting a rule closes the one in force and adds the next. Two coordinators doing it at once
   * would both read the same rule in force, both close it and both add one, leaving two in force. The
   * rule rows are not a lock because there may be none yet, so the lock is taken from the database,
   * per university: it also holds across several instances of the application.
   *
   * @return nothing useful: only the wait matters
   */
  @Query(
      value = "select cast(pg_advisory_xact_lock(7051001, hashtext(:tenantId)) as text)",
      nativeQuery = true)
  String lockTenant(@Param("tenantId") String tenantId);
}
