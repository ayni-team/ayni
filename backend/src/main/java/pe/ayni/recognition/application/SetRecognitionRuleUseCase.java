package pe.ayni.recognition.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * A coordinator sets what their university asks of a student before it considers recognition: the
 * rule that opens recognition, or the next one.
 *
 * <p>Rules are never edited. The rule in force is closed and the new one is added in the same
 * transaction, so what was presented under the old one can still be explained; the requests keep the
 * figures they were submitted with, whatever the rule says afterwards.
 *
 * <p>The university's rules are locked while one is set: two coordinators at once would both read the
 * same rule in force, both close it and both add one. The second waits for the first and then closes
 * what the first added, so there is one rule in force at the end.
 */
@Service
public class SetRecognitionRuleUseCase {

  private final CoordinatorGuard coordinators;
  private final RecognitionRuleRepository rules;
  private final RuleInForce ruleInForce;
  private final Clock clock;

  SetRecognitionRuleUseCase(
      CoordinatorGuard coordinators,
      RecognitionRuleRepository rules,
      RuleInForce ruleInForce,
      Clock clock) {
    this.coordinators = coordinators;
    this.rules = rules;
    this.ruleInForce = ruleInForce;
    this.clock = clock;
  }

  /**
   * @param minimumHours hours of verified sessions the student must have taught
   * @param validFrom the first day the rule applies in the university, or {@code null} for today
   * @return the rule that was added
   * @throws pe.ayni.recognition.domain.model.NotACoordinator when the person is not an active
   *     coordinator
   * @throws java.util.NoSuchElementException when the person is not a user of this university
   * @throws pe.ayni.recognition.domain.model.RecognitionRuleViolation when the hours are not positive
   *     or the day is already gone
   */
  @Transactional
  public RecognitionRule execute(UUID coordinatorId, int minimumHours, LocalDate validFrom) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");

    coordinators.requireActive(coordinatorId);
    String tenantId = TenantContext.require();
    Instant now = clock.instant();
    LocalDate today = ruleInForce.today(tenantId, now);

    RecognitionRule next =
        RecognitionRule.start(UUID.randomUUID(), tenantId, minimumHours, validFrom, today, now);

    // Before reading the rules in force, so that the second of two coordinators reads what the first left.
    rules.lockTenant(tenantId);
    rules.findByTenantIdAndSupersededAtIsNull(tenantId).stream()
        .filter(open -> open.isReplacedBy(next.getValidFrom(), today))
        .forEach(open -> open.supersede(now));
    return rules.save(next);
  }
}
