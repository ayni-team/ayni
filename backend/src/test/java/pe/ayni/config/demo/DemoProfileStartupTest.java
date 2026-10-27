package pe.ayni.config.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.sessions.application.VideoRoomPasses;
import pe.ayni.sessions.application.VideoRoomPasses.VideoPass;
import pe.ayni.sessions.domain.model.ParticipantRole;

/**
 * The application started with the {@code demo} profile and nothing else, as {@code
 * SPRING_PROFILES_ACTIVE=demo} starts it: the values of {@code application-demo.yml} reach the
 * jobs, the clock is the one that moves, and the data of {@code dev} is there with the demo
 * coordinator. One test per scenario of {@code features/demo-profile.feature} that names this
 * class, with the same display name.
 *
 * <p>Its jobs run every few seconds, as in a presentation, so the context is closed after the
 * class: left in the cache it would keep running them under the other tests.
 */
@SpringBootTest
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoProfileStartupTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = DemoTestDatabase.INSTANCE;

  private static final String EVERY_MINUTE = "0 * * * * *";

  @Autowired private Clock clock;
  @Autowired private Environment environment;
  @Autowired private ObjectProvider<ScheduledTaskHolder> scheduledTaskHolders;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private VideoRoomPasses videoPasses;

  private List<ScheduledTask> scheduledTasks() {
    return scheduledTaskHolders.stream()
        .flatMap(holder -> holder.getScheduledTasks().stream())
        .toList();
  }

  private ScheduledTask jobOf(String jobClass) {
    return scheduledTasks().stream()
        .filter(task -> task.toString().contains("." + jobClass + "."))
        .findFirst()
        .orElseThrow(() -> new AssertionError(jobClass + " is not scheduled"));
  }

  @Test
  @DisplayName("The jobs run often enough to react to the clock")
  void theJobsRunOftenEnoughToReactToTheClock() {
    assertThat(clock).isInstanceOf(AdjustableClock.class);

    // A cron is matched against the real time: every minute, whatever the clock says.
    for (String cronJob : List.of("CreditExpiryJob", "HourBlockHorizonJob")) {
      assertThat(jobOf(cronJob).getTask())
          .as(cronJob)
          .isInstanceOfSatisfying(
              CronTask.class, cron -> assertThat(cron.getExpression()).isEqualTo(EVERY_MINUTE));
    }

    Map<String, Duration> atMost =
        Map.of(
            "ExpiredHoldsJob", Duration.ofSeconds(5),
            "PresenceCheckJob", Duration.ofSeconds(5),
            "SessionClosingJob", Duration.ofSeconds(5),
            "TutorNoShowJob", Duration.ofSeconds(5),
            "PendingPurchaseReconciliationJob", Duration.ofSeconds(5),
            "RepeatedPairJob", Duration.ofSeconds(10));
    atMost.forEach(
        (job, delay) ->
            assertThat(jobOf(job).getTask())
                .as(job)
                .isInstanceOfSatisfying(
                    FixedDelayTask.class,
                    fixed -> assertThat(fixed.getIntervalDuration()).isEqualTo(delay)));
  }

  @Test
  @DisplayName("The demo starts with the data of dev and an active coordinator")
  void theDemoStartsWithTheDataOfDevAndAnActiveCoordinator() {
    assertThat(environment.getActiveProfiles()).contains("demo");
    assertThat(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev")))
        .isTrue();

    Map<String, Object> coordinator =
        jdbc.queryForMap(
            "select tenant_id, role, status, email from identity.users where id = ?",
            UUID.fromString("44444444-4444-4444-8444-444444444444"));
    assertThat(coordinator)
        .containsEntry("tenant_id", "UPC")
        .containsEntry("role", "COORDINATOR")
        .containsEntry("status", "ACTIVE")
        .containsEntry("email", "coordinator@upc.edu.pe");

    assertThat(
            jdbc.queryForObject(
                "select count(*) from identity.platform_admins where email = 'admin@ayni.local'",
                Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from identity.users where tenant_id = 'UPC' and role = 'STUDENT'",
                Long.class))
        .isGreaterThanOrEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from recognition.rules where tenant_id = 'UPC'", Long.class))
        .isGreaterThanOrEqualTo(1L);
    // The seeded university has the baseline credit policy a registered one is born with.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from identity.credit_policies"
                    + " where tenant_id = 'UPC' and kind = 'BASELINE' and superseded_at is null",
                Long.class))
        .isEqualTo(1L);

    // A session lasts a year here; an access link keeps its ten minutes.
    assertThat(environment.getProperty("ayni.identity.session-ttl", Duration.class))
        .isEqualTo(Duration.ofDays(365));
    assertThat(environment.getProperty("ayni.identity.access-link-ttl", Duration.class))
        .isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  @DisplayName("The demo starts with the two universities of the academic documents")
  void theDemoStartsWithTheTwoUniversitiesOfTheAcademicDocuments() {
    // Both registered from their documents, with their brand and the grade of the document.
    assertThat(
            jdbc.queryForList(
                "select code || ' ' || name || ' ' || logo_url || ' ' || minimum_teaching_grade"
                    + " from identity.tenants where code in ('UPC', 'USIL') and status = 'ACTIVE'"
                    + " order by code",
                String.class))
        .containsExactly(
            "UPC Universidad Peruana de Ciencias Aplicadas /logos/upc.png 15.00",
            "USIL Universidad San Ignacio de Loyola /logos/usil.jpg 16.00");

    // Each with its curriculum loaded.
    assertThat(courseCount("UPC")).isEqualTo(57L);
    assertThat(courseCount("USIL")).isEqualTo(64L);

    // The four people of the demonstration, and no other student: the rest activate live.
    assertThat(
            jdbc.queryForList(
                "select tenant_id || ' ' || full_name || ' ' || onboarding_step || ' ' || current_cycle"
                    + " from identity.users where role = 'STUDENT' and tenant_id in ('UPC', 'USIL')"
                    + " order by tenant_id, full_name",
                String.class))
        .containsExactly(
            "UPC Ana Torres PROFILE 4",
            "UPC Bruno Salas COMPLETED 8",
            "USIL Lucía Paredes PROFILE 4",
            "USIL Mateo Ríos COMPLETED 8");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from identity.users where email = 'u202500003@upc.edu.pe'", Long.class))
        .isZero();

    // The two tutors teach courses of their own university, and have hours to be booked in.
    for (String tutor :
        List.of("22222222-2222-4222-8222-222222222222", "77777777-7777-4777-8777-777777777777")) {
      assertThat(
              jdbc.queryForObject(
                  """
                  select count(*) from skills.offered_skills skill
                  join skills.catalog_items item on item.id = skill.catalog_item_id
                  where skill.tutor_id = ? and skill.status = 'ENABLED'
                    and item.tenant_id = skill.tenant_id
                  """,
                  Long.class,
                  UUID.fromString(tutor)))
          .as(tutor)
          .isPositive();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking.availability_patterns where tutor_id = ?",
                  Long.class,
                  UUID.fromString(tutor)))
          .as(tutor)
          .isPositive();
    }

    // A coordinator of each.
    assertThat(
            jdbc.queryForList(
                "select tenant_id || ' ' || email from identity.users"
                    + " where role = 'COORDINATOR' and status = 'ACTIVE' and tenant_id in ('UPC', 'USIL')"
                    + " order by tenant_id",
                String.class))
        .containsExactly("UPC coordinator@upc.edu.pe", "USIL coordinator@usil.edu.pe");
  }

  private long courseCount(String university) {
    return jdbc.queryForObject(
        "select count(*) from skills.catalog_items where tenant_id = ? and course_code is not null",
        Long.class,
        university);
  }

  @Test
  @DisplayName("under the demo profile a video pass of a moved clock is signed with the real time")
  void aVideoPassOfAMovedClockIsSignedWithTheRealTime() throws Exception {
    AdjustableClock adjustable = (AdjustableClock) clock;
    adjustable.advance(Duration.ofDays(3));
    try {
      Instant before = Instant.now();
      Instant now = clock.instant();
      VideoPass pass =
          videoPasses
              .issue("ayni-room", UUID.randomUUID(), ParticipantRole.STUDENT, now,
                  now.plus(Duration.ofHours(1)))
              .orElseThrow();
      Instant after = Instant.now();

      JsonNode claims =
          new ObjectMapper().readTree(Base64.getUrlDecoder().decode(pass.token().split("\\.")[1]));
      assertThat(claims.path("iat").asLong())
          .isBetween(before.getEpochSecond(), after.getEpochSecond());
      assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(300);
    } finally {
      adjustable.reset();
    }
  }
}
