package pe.ayni.recognition.infrastructure;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.shared.demo.DemoCast;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The recognition rule of each demonstration university, as if its coordinator had set it.
 *
 * <p>Without a rule recognition is closed: nobody can see their progress or ask for anything. Under
 * the {@code dev} profile UPC and USIL ask for three hours taught, few enough for the tutors of the
 * history of the {@code demo} profile to have asked already. It lives here and not in a migration
 * on purpose, as in {@code DemoSkillsData}: a migration runs everywhere and cannot be taken back.
 *
 * <p>It runs once. The identifiers are fixed so that a second start finds each rule and leaves it
 * alone; a database seeded when UPC asked for twenty hours keeps its twenty.
 */
@Component
@Profile("dev")
class DemoRecognitionData implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoRecognitionData.class);

  static final int MINIMUM_HOURS = 3;

  private static final Map<String, UUID> RULES =
      Map.of(
          DemoCast.UPC, UUID.fromString("c0000000-0000-4000-8000-000000000001"),
          DemoCast.USIL, UUID.fromString("c0000000-0000-4000-8000-000000000002"));

  private final RecognitionRuleRepository rules;
  private final Clock clock;

  DemoRecognitionData(RecognitionRuleRepository rules, Clock clock) {
    this.rules = rules;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    // Row level security (TS01): each rule is written, and looked for, as its university's.
    RULES.forEach(
        (university, rule) -> {
          try {
            TenantContext.runAs(university, () -> fill(university, rule));
          } catch (RuntimeException failure) {
            // Demonstration data must not keep the application from starting.
            log.error("Could not set the demo recognition rule of {}", university, failure);
          }
        });
  }

  private void fill(String university, UUID rule) {
    if (rules.existsById(rule)) {
      return;
    }
    Instant now = clock.instant();
    rules.save(
        new RecognitionRule(
            rule,
            university,
            MINIMUM_HOURS,
            new BigDecimal("4.00"),
            LocalDate.ofInstant(now, ZoneOffset.UTC).minusYears(1),
            now));
    log.info("Demo recognition rule ready: {} asks for {} hours", university, MINIMUM_HOURS);
  }
}
