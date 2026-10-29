package pe.ayni.config.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.audit.application.VerifyActivityLogIntegrityUseCase;
import pe.ayni.matching.application.FoundOffer;
import pe.ayni.matching.application.SearchOffersUseCase;
import pe.ayni.shared.demo.DemoCast;
import pe.ayni.shared.demo.DemoCast.Person;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;
import pe.ayni.wallet.application.VerifyLedgerIntegrity;

/**
 * The data of the demonstration, as {@code SPRING_PROFILES_ACTIVE=demo} leaves it: the people and
 * their balances, the tutors and their hours, the tutorings ahead, and the history played with the
 * clock moved back. One test per scenario of {@code features/demo-seed.feature}, with the same
 * display name.
 *
 * <p>It shares the database of the other tests of the profile, so whichever of them starts first
 * seeds it and the rest find it seeded, as a second start would.
 */
@SpringBootTest
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoSeedAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = DemoTestDatabase.INSTANCE;

  @Autowired private Clock clock;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private WalletApi wallet;
  @Autowired private SearchOffersUseCase search;
  @Autowired private VerifyLedgerIntegrity ledger;
  @Autowired private VerifyActivityLogIntegrityUseCase activityLog;
  @Autowired private ConfigurableApplicationContext context;

  @Test
  @DisplayName("The people of the demonstration are active with their balances")
  void thePeopleOfTheDemonstrationAreActiveWithTheirBalances() {
    for (String university : List.of(DemoCast.UPC, DemoCast.USIL)) {
      assertThat(
              jdbc.queryForList(
                  "select distinct onboarding_step from identity.users"
                      + " where tenant_id = ? and role = 'STUDENT' and status = 'ACTIVE'",
                  String.class,
                  university))
          .as(university)
          .contains("PROFILE", "ACADEMIC_RECORD", "LEARNING_INTERESTS", "COMPLETED");
    }

    // Nobody books with these four, so their balances stay what they were given.
    Map<Person, Integer> balances = new LinkedHashMap<>();
    balances.put(DemoCast.ANDRES_ZEGARRA, 0);
    balances.put(DemoCast.ANA_FLORES, 1);
    balances.put(DemoCast.JULIAN_ARCE, 5);
    balances.put(DemoCast.VALERIA_MEDINA, 15);
    balances.put(DemoCast.BRUNO_CASTILLO, 0);
    balances.put(DemoCast.SEBASTIAN_GUTIERREZ, 1);
    balances.put(DemoCast.LUCIA_SALAZAR, 5);
    balances.put(DemoCast.DIEGO_ESPINOZA, 15);
    balances.forEach(
        (person, credits) ->
            assertThat(balanceOf(person)).as(person.fullName()).isEqualTo(credits));

    for (Person mixed : List.of(DemoCast.MILAGROS_CASTILLO, DemoCast.ROCIO_PALOMINO)) {
      assertThat(
              jdbc.queryForList(
                  "select distinct lot.credit_type from wallet.credit_lots lot"
                      + " join wallet.credit_accounts account on account.id = lot.account_id"
                      + " where account.user_id = ?",
                  String.class,
                  mixed.id()))
          .as(mixed.fullName())
          .contains("SEED", "ALLOCATED", "PURCHASED");
    }
    for (Person expiring : List.of(DemoCast.ALEJANDRA_CHAVEZ, DemoCast.GABRIEL_NAVARRO)) {
      Instant expiresAt =
          jdbc.queryForObject(
              "select min(lot.expires_at) from wallet.credit_lots lot"
                  + " join wallet.credit_accounts account on account.id = lot.account_id"
                  + " where account.user_id = ? and lot.remaining_amount > 0",
              Instant.class,
              expiring.id());
      assertThat(Duration.between(clock.instant(), expiresAt))
          .as(expiring.fullName())
          .isBetween(Duration.ofHours(24), Duration.ofDays(2));
      assertThat(noticesOf(expiring.id(), "CREDITS_EXPIRING")).isPositive();
    }
  }

  @Test
  @DisplayName("The search finds several courses and several tutors, and nobody for some courses")
  void theSearchFindsSeveralCoursesAndSeveralTutors() {
    Instant from = clock.instant();
    Instant to = from.plus(Duration.ofDays(7));
    for (String university : List.of(DemoCast.UPC, DemoCast.USIL)) {
      Person student = DemoCast.UPC.equals(university) ? DemoCast.VALERIA_MEDINA : DemoCast.DIEGO_ESPINOZA;
      List<UUID> taught =
          jdbc.queryForList(
              "select distinct catalog_item_id from skills.offered_skills"
                  + " where tenant_id = ? and status = 'ENABLED'",
              UUID.class,
              university);
      Set<UUID> courses = new HashSet<>();
      Set<UUID> tutors = new HashSet<>();
      TenantContext.runAs(
          university,
          () -> {
            for (UUID course : taught) {
              for (FoundOffer offer :
                  search.execute(student.id(), course, from, to, 0, 50).offers()) {
                if (offer.startsAt().isBefore(to)) {
                  courses.add(offer.catalogItemId());
                  tutors.add(offer.tutorId());
                }
              }
            }
          });
      assertThat(courses).as(university).hasSizeGreaterThanOrEqualTo(12);
      assertThat(tutors).as(university).hasSizeGreaterThanOrEqualTo(3);

      String uncovered = DemoCast.UPC.equals(university) ? "SI643" : "USIL026";
      UUID course =
          jdbc.queryForObject(
              "select id from skills.catalog_items where tenant_id = ? and course_code = ?",
              UUID.class,
              university,
              uncovered);
      assertThat(
              TenantContext.callAs(
                  university, () -> search.execute(student.id(), course, from, to, 0, 20).offers()))
          .as(uncovered)
          .isEmpty();
    }
  }

  @Test
  @DisplayName("A student has a tutoring ahead and one cancelled")
  void aStudentHasATutoringAheadAndOneCancelled() {
    Instant now = clock.instant();
    for (Person student : List.of(DemoCast.MILAGROS_CASTILLO, DemoCast.ROCIO_PALOMINO)) {
      List<Instant> ahead =
          jdbc.queryForList(
              "select starts_at from booking.bookings"
                  + " where student_id = ? and status = 'CONFIRMED' and starts_at > ?",
              Instant.class,
              student.id(),
              java.sql.Timestamp.from(now));
      assertThat(ahead).as(student.fullName()).isNotEmpty();
      assertThat(ahead)
          .allSatisfy(
              start ->
                  assertThat(Duration.between(now, start))
                      .isBetween(Duration.ofDays(2).minusHours(1), Duration.ofDays(8)));

      UUID cancelled =
          jdbc.queryForObject(
              "select id from booking.bookings where student_id = ? and status = 'CANCELLED'"
                  + " order by created_at limit 1",
              UUID.class,
              student.id());
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from wallet.ledger_entries where reference_id = ? and reason = 'BOOKING_REFUND'",
                  Long.class,
                  cancelled))
          .isPositive();
    }
    // The tutors have it in their agenda.
    assertThat(
            jdbc.queryForObject(
                "select count(distinct tutor_id) from booking.bookings"
                    + " where status = 'CONFIRMED' and starts_at > ?",
                Long.class,
                java.sql.Timestamp.from(now)))
        .isGreaterThanOrEqualTo(6L);
  }

  @Test
  @DisplayName("The history has completed and rated sessions")
  void theHistoryHasCompletedAndRatedSessions() {
    for (String university : List.of(DemoCast.UPC, DemoCast.USIL)) {
      assertThat(sessions(university, "COMPLETED")).as(university).isBetween(4L, 6L);
      assertThat(sessions(university, "ABANDONED")).as(university).isEqualTo(2L);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from reputation.ratings where tenant_id = ?"
                      + " and direction = 'STUDENT_TO_TUTOR'",
                  Long.class,
                  university))
          .isGreaterThanOrEqualTo(4L);
      assertThat(
              jdbc.queryForList(
                  "select distinct stars from reputation.ratings where tenant_id = ?"
                      + " and stars is not null",
                  Integer.class,
                  university))
          .hasSizeGreaterThanOrEqualTo(3);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from sessions.whiteboards where tenant_id = ? and has_activity",
                  Long.class,
                  university))
          .isEqualTo(2L);
      assertThat(
              jdbc.queryForList(
                  "select status from recognition.requests where tenant_id = ? order by status",
                  String.class,
                  university))
          .containsExactly("APPROVED", "SUBMITTED", "SUBMITTED");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from analytics.uncovered_demand where tenant_id = ?",
                  Long.class,
                  university))
          .isPositive();
    }
    // The tutor who did not come refunded the student, who was told.
    assertThat(noticesOf(DemoCast.ANA.id(), "TUTOR_NO_SHOW")).isEqualTo(1L);

    for (Person person : List.of(DemoCast.ANA, DemoCast.BRUNO)) {
      assertThat(
              jdbc.queryForObject(
                  "select count(distinct kind) from notifications.notifications"
                      + " where recipient_id = ? and kind not in ('ACCESS_LINK', 'PRESENCE_CODE')",
                  Long.class,
                  person.id()))
          .as(person.fullName())
          .isGreaterThanOrEqualTo(3L);
      assertThat(
              jdbc.queryForList(
                  "select distinct read_at is null from notifications.notifications"
                      + " where recipient_id = ? and kind not in ('ACCESS_LINK', 'PRESENCE_CODE')",
                  Boolean.class,
                  person.id()))
          .as(person.fullName())
          .containsExactlyInAnyOrder(true, false);
    }
  }

  @Test
  @DisplayName("The rest of the students of the documents have no account")
  void theRestOfTheStudentsOfTheDocumentsHaveNoAccount() {
    Set<String> cast = new HashSet<>();
    DemoCast.STUDENTS.forEach(person -> cast.add(person.email()));
    assertThat(
            jdbc.queryForList(
                "select email from identity.users where role = 'STUDENT'"
                    + " and tenant_id in ('UPC', 'USIL')",
                String.class))
        .containsExactlyInAnyOrderElementsOf(cast);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from identity.users where email = 'u202500003@upc.edu.pe'",
                Long.class))
        .isZero();
  }

  @Test
  @DisplayName("The ledger and the audit are intact and the clock is where it was")
  void theLedgerAndTheAuditAreIntactAndTheClockIsWhereItWas() {
    for (String university : List.of(DemoCast.UPC, DemoCast.USIL)) {
      UUID coordinator =
          DemoCast.UPC.equals(university) ? DemoCast.UPC_COORDINATOR : DemoCast.USIL_COORDINATOR;
      assertThat(TenantContext.callAs(university, ledger::forCurrentUniversity).intact())
          .as(university)
          .isTrue();
      assertThat(TenantContext.callAs(university, () -> activityLog.execute(coordinator)).intact())
          .as(university)
          .isTrue();
    }
    assertThat(((AdjustableClock) clock).offset()).isZero();
  }

  @Test
  @DisplayName("A second start duplicates nothing")
  void aSecondStartDuplicatesNothing() throws Exception {
    Map<String, Long> before = rowCounts();

    for (ApplicationRunner runner : context.getBeansOfType(ApplicationRunner.class).values()) {
      runner.run(null);
    }
    context.publishEvent(
        new ApplicationReadyEvent(
            new SpringApplication(), new String[0], context, Duration.ZERO));

    assertThat(rowCounts()).isEqualTo(before);
    assertThat(((AdjustableClock) clock).offset()).isZero();
  }

  private Map<String, Long> rowCounts() {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (String table :
        List.of(
            "identity.users", "skills.offered_skills", "skills.learning_interests",
            "booking.availability_patterns", "booking.availability_exceptions",
            "booking.availability_pauses", "booking.bookings", "sessions.sessions",
            "reputation.ratings", "recognition.requests", "recognition.rules",
            "wallet.ledger_entries", "notifications.notifications",
            "analytics.uncovered_demand")) {
      counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
    }
    return counts;
  }

  private int balanceOf(Person person) {
    return TenantContext.callAs(
        person.university(), () -> wallet.balanceOf(person.id()).available().amount());
  }

  private long sessions(String university, String status) {
    return jdbc.queryForObject(
        "select count(*) from sessions.sessions where tenant_id = ? and status = ?",
        Long.class,
        university,
        status);
  }

  private long noticesOf(UUID recipient, String kind) {
    return jdbc.queryForObject(
        "select count(*) from notifications.notifications where recipient_id = ? and kind = ?",
        Long.class,
        recipient,
        kind);
  }
}
