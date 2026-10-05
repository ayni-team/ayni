package pe.ayni.recognition.infrastructure;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.recognition.domain.model.RecognitionRule;

/**
 * A recognition rule to look at while no story lets a university set its own.
 *
 * <p>Without a rule recognition is closed: nobody can see their progress or ask for anything. Under
 * the {@code dev} profile the demonstration university asks for 20 hours. It lives here and not in a
 * migration on purpose, as in {@code DemoSkillsData}: a migration runs everywhere and cannot be taken
 * back.
 *
 * <p>It runs once. The identifier is fixed so that a second start finds the rule and leaves it alone.
 */
@Component
@Profile("dev")
class DemoRecognitionData implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoRecognitionData.class);

  private static final String TENANT = "UPC";
  private static final UUID RULE = UUID.fromString("c0000000-0000-4000-8000-000000000001");

  private final RecognitionRuleRepository rules;
  private final Clock clock;

  DemoRecognitionData(RecognitionRuleRepository rules, Clock clock) {
    this.rules = rules;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (rules.existsById(RULE)) {
      return;
    }
    Instant now = clock.instant();
    rules.save(
        new RecognitionRule(
            RULE,
            TENANT,
            20,
            new BigDecimal("4.00"),
            LocalDate.ofInstant(now, ZoneOffset.UTC).minusYears(1),
            now));
    log.info("Demo recognition rule ready: {} asks for 20 hours", TENANT);
  }
}
