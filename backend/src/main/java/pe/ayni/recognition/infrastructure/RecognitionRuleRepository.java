package pe.ayni.recognition.infrastructure;

import java.time.LocalDate;
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
}
