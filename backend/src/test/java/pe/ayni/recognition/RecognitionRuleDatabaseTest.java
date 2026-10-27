package pe.ayni.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;

/** Which rule of a university is in force, against a real PostgreSQL. */
@SpringBootTest
class RecognitionRuleDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

  @Autowired private RecognitionRuleRepository rules;
  @Autowired private JdbcTemplate jdbc;

  private String tenant;

  @BeforeEach
  void aUniversityOfItsOwn() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
  }

  private RecognitionRule rule(String tenantId, int hours, LocalDate from) {
    return rules.save(new RecognitionRule(UUID.randomUUID(), tenantId, hours, null, from, NOW));
  }

  @Test
  @DisplayName("a university without a rule has none in force")
  void noRule() {
    assertThat(rules.findInForce(tenant, TODAY)).isEmpty();
  }

  @Test
  @DisplayName("the rule in force is the latest one that has started")
  void theLatestStartedRule() {
    rule(tenant, 10, TODAY.minusYears(2));
    RecognitionRule latest = rule(tenant, 20, TODAY.minusDays(3));
    rule(tenant, 40, TODAY.plusDays(30));

    assertThat(rules.findInForce(tenant, TODAY)).map(RecognitionRule::getId).contains(latest.getId());
  }

  @Test
  @DisplayName("a rule that starts tomorrow is not in force today but is tomorrow")
  void aRuleThatStartsLater() {
    RecognitionRule future = rule(tenant, 20, TODAY.plusDays(1));

    assertThat(rules.findInForce(tenant, TODAY)).isEmpty();
    assertThat(rules.findInForce(tenant, TODAY.plusDays(1))).map(RecognitionRule::getId).contains(future.getId());
  }

  @Test
  @DisplayName("a superseded rule is no longer in force and the previous one is not brought back by it")
  void aSupersededRule() {
    RecognitionRule old = rule(tenant, 10, TODAY.minusDays(30));
    RecognitionRule replacement = rule(tenant, 20, TODAY.minusDays(1));

    replacement.supersede(NOW);
    rules.save(replacement);

    assertThat(rules.findInForce(tenant, TODAY)).map(RecognitionRule::getId).contains(old.getId());
    old.supersede(NOW);
    rules.save(old);
    assertThat(rules.findInForce(tenant, TODAY)).isEmpty();
  }

  @Test
  @DisplayName("rules of another university are not read")
  void anotherUniversity() {
    rule("O" + tenant, 5, TODAY.minusDays(1));

    assertThat(rules.findInForce(tenant, TODAY)).isEmpty();
  }

  @Test
  @DisplayName("the database refuses zero hours and a rating off the scale")
  void theDatabaseRefusesWhatTheEntityRefuses() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into recognition.rules (id, tenant_id, minimum_hours, valid_from) values (?, ?, 0, ?)",
                    UUID.randomUUID(),
                    tenant,
                    TODAY))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into recognition.rules (id, tenant_id, minimum_hours, minimum_rating, valid_from)"
                        + " values (?, ?, 10, 5.5, ?)",
                    UUID.randomUUID(),
                    tenant,
                    TODAY))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("the entity refuses what the table refuses")
  void theEntityRefusesBadValues() {
    assertThatThrownBy(() -> new RecognitionRule(UUID.randomUUID(), tenant, 0, null, TODAY, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new RecognitionRule(UUID.randomUUID(), tenant, 10, new BigDecimal("5.01"), TODAY, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new RecognitionRule(UUID.randomUUID(), tenant, 10, new BigDecimal("-0.1"), TODAY, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
