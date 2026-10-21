package pe.ayni.recognition.application;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.shared.tenancy.TenantContext;

/** What a coordinator reads of the rule their university has in force today. */
@Service
public class CurrentRuleQuery {

  private final CoordinatorGuard coordinators;
  private final RuleInForce rules;
  private final Clock clock;

  CurrentRuleQuery(CoordinatorGuard coordinators, RuleInForce rules, Clock clock) {
    this.coordinators = coordinators;
    this.rules = rules;
    this.clock = clock;
  }

  /**
   * @throws pe.ayni.recognition.domain.model.NotACoordinator when the person is not an active
   *     coordinator
   * @throws NoSuchElementException when the person is not a user of this university, or the university
   *     has not set a rule yet
   */
  @Transactional(readOnly = true)
  public RecognitionRule of(UUID coordinatorId) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");

    coordinators.requireActive(coordinatorId);
    return rules
        .of(TenantContext.require(), clock.instant())
        .orElseThrow(() -> new NoSuchElementException("the university has not set a recognition rule"));
  }
}
