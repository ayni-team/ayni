package pe.ayni.recognition.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;

/**
 * The rule of recognition a university has in force at a moment, read on the university's own day.
 *
 * <p>A rule starts on a date, and a date is a day of the university: a rule that starts on the 16th
 * is not in force at 21:00 of the 15th in Lima, although it is already the 16th in UTC. The zone is
 * the university's, asked of identity, never the server's.
 */
@Component
class RuleInForce {

  private final RecognitionRuleRepository rules;
  private final IdentityApi identity;

  RuleInForce(RecognitionRuleRepository rules, IdentityApi identity) {
    this.rules = rules;
    this.identity = identity;
  }

  Optional<RecognitionRule> of(String tenantId, Instant now) {
    ZoneId zone = ZoneId.of(identity.requireTenant(tenantId).timezone());
    return rules.findInForce(tenantId, LocalDate.ofInstant(now, zone));
  }
}
