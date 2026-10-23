package pe.ayni.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.analytics.application.ProjectUsageUseCase;
import pe.ayni.analytics.application.UncoveredDemandQueue;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.DemandUncovered;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The uncovered demand of US53, over HTTP, against a real PostgreSQL, with people who signed in.
 *
 * <p>The scenario of {@code features/US53-usage-indicators.feature} that is not the usage figures,
 * with its name, and what the projection promises beyond it. Nothing is inserted: students search
 * for courses that nobody offers through the real search, matching publishes {@code
 * DemandUncovered}, analytics counts it, and a coordinator reads it. The counting happens on the
 * workers of {@link UncoveredDemandQueue}, so each test waits for the queue to be idle instead of
 * guessing a time.
 *
 * <p>The university is in Lima, five hours behind UTC, and the clock is the test's, so half past ten
 * at night there is already the next day in UTC and the tests can tell the two days apart.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@Import(AnalyticsTestConfig.class)
class UncoveredDemandAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = AnalyticsTestDatabase.INSTANCE;

  private static final ZoneId LIMA = ZoneId.of("America/Lima");
  private static final Duration PATIENCE = Duration.ofSeconds(20);

  /** The day the searches are made, in Lima: tomorrow, so the clock never goes back. */
  private static final LocalDate DAY = LocalDate.now(LIMA).plusDays(1);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private ProjectUsageUseCase projection;
  @Autowired private UncoveredDemandQueue queue;
  @Autowired private MutableClock clock;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  private SignedInPeople people;
  private String tenant;
  private String otherTenant;
  private Person coordinator;
  private Person otherCoordinator;
  private Person ana;
  private Person bruno;
  private Person otherStudent;

  private final UUID algebra = UUID.randomUUID();
  private final UUID calculus = UUID.randomUUID();
  private final UUID physics = UUID.randomUUID();

  /** Background: two universities in Lima, each with a coordinator, and students. */
  @BeforeEach
  void twoUniversitiesWithTheirPeople() {
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("U");
    otherTenant = people.aUniversity("V");
    coordinator = people.aCoordinator(tenant);
    otherCoordinator = people.aCoordinator(otherTenant);
    ana = people.aStudent(tenant);
    bruno = people.aStudent(tenant);
    otherStudent = people.aStudent(otherTenant);
  }

  // ---------------------------------------------------------------------------------------------
  // The scenario of the story
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("Uncovered demand")
  void uncoveredDemand() throws Exception {
    // Students search for courses that no tutor offers: nothing has been offered in the whole
    // university, so every window is empty.
    at(DAY, 10, 0);
    searchFor(ana, algebra).andExpect(status().isOk());
    searchFor(bruno, algebra).andExpect(status().isOk());
    searchFor(ana, algebra).andExpect(status().isOk());
    searchFor(bruno, calculus).andExpect(status().isOk());
    // Half past ten at night in Lima, which in UTC is already the next day.
    at(DAY, 22, 30);
    searchFor(ana, calculus).andExpect(status().isOk());
    at(DAY.plusDays(1), 10, 0);
    searchFor(bruno, physics).andExpect(status().isOk());
    // Outside the period that is read.
    at(DAY.plusDays(10), 10, 0);
    searchFor(bruno, UUID.randomUUID()).andExpect(status().isOk());
    // Another university searched for the same course, more often.
    at(DAY, 10, 0);
    for (int i = 0; i < 5; i++) {
      searchFor(otherStudent, algebra).andExpect(status().isOk());
    }
    idle();

    // When a coordinator reads the indicators
    String mine = read(coordinator, DAY, DAY.plusDays(5));

    // Then they see which courses are sought and not covered, the most sought first.
    assertThat(JsonPath.<List<String>>read(mine, "$.uncoveredDemand[*].catalogItemId"))
        .containsExactly(algebra.toString(), calculus.toString(), physics.toString());
    assertThat(JsonPath.<List<Integer>>read(mine, "$.uncoveredDemand[*].searches"))
        .containsExactly(3, 2, 1);

    // Courses and counts only: no student, and no name of a course either.
    assertThat(mine).doesNotContain(ana.id().toString(), bruno.id().toString());
    assertThat(JsonPath.<List<Object>>read(mine, "$.uncoveredDemand[0].*")).hasSize(2);

    // The other university reads only its own searches.
    String theirs = read(otherCoordinator, DAY, DAY.plusDays(5));
    assertThat(JsonPath.<List<String>>read(theirs, "$.uncoveredDemand[*].catalogItemId"))
        .containsExactly(algebra.toString());
    assertThat(JsonPath.<List<Integer>>read(theirs, "$.uncoveredDemand[*].searches"))
        .containsExactly(5);
  }

  // ---------------------------------------------------------------------------------------------
  // What the projection promises, beyond the scenario
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("The uncovered demand is counted on the day of the university")
  void theUncoveredDemandIsCountedOnTheDayOfTheUniversity() throws Exception {
    at(DAY, 22, 30);
    searchFor(ana, calculus).andExpect(status().isOk());
    at(DAY.plusDays(1), 10, 0);
    searchFor(ana, physics).andExpect(status().isOk());
    idle();

    // 22:30 in Lima belongs to the day it is in Lima, not to the one UTC says.
    String thatDay = read(coordinator, DAY, DAY);
    assertThat(JsonPath.<List<String>>read(thatDay, "$.uncoveredDemand[*].catalogItemId"))
        .containsExactly(calculus.toString());
    String nextDay = read(coordinator, DAY.plusDays(1), DAY.plusDays(1));
    assertThat(JsonPath.<List<String>>read(nextDay, "$.uncoveredDemand[*].catalogItemId"))
        .containsExactly(physics.toString());
    assertThat(
            jdbc.queryForObject(
                "select day from analytics.uncovered_demand where tenant_id = ? and catalog_item_id = ?",
                LocalDate.class,
                tenant,
                calculus))
        .isEqualTo(DAY);
  }

  @Test
  @DisplayName("Courses with the same searches come in the order of their identifier")
  void coursesWithTheSameSearchesComeInTheOrderOfTheirIdentifier() throws Exception {
    List<UUID> courses = new ArrayList<>(List.of(algebra, calculus, physics));
    at(DAY, 10, 0);
    for (UUID course : courses) {
      searchFor(ana, course).andExpect(status().isOk());
      searchFor(bruno, course).andExpect(status().isOk());
    }
    idle();

    String body = read(coordinator, DAY, DAY);

    // The database sorts uuids as their text, byte by byte.
    List<String> expected = courses.stream().map(UUID::toString).sorted().toList();
    assertThat(JsonPath.<List<String>>read(body, "$.uncoveredDemand[*].catalogItemId"))
        .containsExactlyElementsOf(expected);
  }

  @Test
  @DisplayName("Only the twenty most searched courses are listed")
  void onlyTheTwentyMostSearchedCoursesAreListed() throws Exception {
    List<UUID> courses = new ArrayList<>();
    at(DAY, 10, 0);
    for (int i = 0; i < 21; i++) {
      UUID course = UUID.randomUUID();
      courses.add(course);
      searchFor(ana, course).andExpect(status().isOk());
    }
    // One of them is searched twice, so it leads.
    searchFor(bruno, courses.getFirst()).andExpect(status().isOk());
    idle();

    String body = read(coordinator, DAY, DAY);

    List<String> listed = JsonPath.read(body, "$.uncoveredDemand[*].catalogItemId");
    assertThat(listed).hasSize(20).first().isEqualTo(courses.getFirst().toString());
    assertThat(JsonPath.<Integer>read(body, "$.uncoveredDemand[0].searches")).isEqualTo(2);
    // The one left out is the last of those with a single search, by identifier.
    String leftOut =
        courses.stream().map(UUID::toString).filter(id -> !listed.contains(id)).findFirst().orElseThrow();
    assertThat(leftOut)
        .isEqualTo(courses.stream().skip(1).map(UUID::toString).max(String::compareTo).orElseThrow());
  }

  @Test
  @DisplayName("A period with no uncovered searches lists none")
  void aPeriodWithNoUncoveredSearchesListsNone() throws Exception {
    String body = read(coordinator, DAY, DAY.plusDays(5));

    assertThat(JsonPath.<List<?>>read(body, "$.uncoveredDemand")).isEmpty();
  }

  @Test
  @DisplayName("Searches at the same moment are all counted")
  void searchesAtTheSameMomentAreAllCounted() throws Exception {
    int deliveries = 8;
    for (int round = 0; round < 5; round++) {
      // A course never searched, so the first of the eight has to create the row.
      UUID course = UUID.randomUUID();
      DemandUncovered event =
          new DemandUncovered(
              tenant, course, Instant.parse("2026-11-05T14:00:00Z"),
              Instant.parse("2026-11-05T18:00:00Z"), Instant.parse("2026-11-04T15:00:00Z"));
      CyclicBarrier barrier = new CyclicBarrier(deliveries);
      ExecutorService pool = Executors.newFixedThreadPool(deliveries);
      try {
        List<Future<?>> running = new ArrayList<>();
        for (int i = 0; i < deliveries; i++) {
          running.add(
              pool.submit(
                  () -> {
                    barrier.await();
                    TenantContext.runAs(tenant, () -> projection.demandUncovered(event));
                    return null;
                  }));
        }
        for (Future<?> delivery : running) {
          delivery.get();
        }
      } finally {
        pool.shutdownNow();
      }

      // Every delivery counted: none lost to another's update, none refused for the same key.
      assertThat(
              jdbc.queryForObject(
                  "select searches from analytics.uncovered_demand where tenant_id = ? and catalog_item_id = ?",
                  Integer.class,
                  tenant,
                  course))
          .as("round %d", round)
          .isEqualTo(deliveries);
    }
  }

  @Test
  @DisplayName("A search that cannot be counted does not break who published")
  void aSearchThatCannotBeCountedDoesNotBreakWhoPublished() throws Exception {
    String unknown = "X" + UUID.randomUUID().toString().substring(0, 8);
    DemandUncovered event =
        new DemandUncovered(
            unknown, algebra, Instant.parse("2026-11-05T14:00:00Z"),
            Instant.parse("2026-11-05T18:00:00Z"), Instant.parse("2026-11-04T15:00:00Z"));

    assertThatCode(() -> transactions.executeWithoutResult(status -> events.publishEvent(event)))
        .doesNotThrowAnyException();

    idle();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from analytics.uncovered_demand where tenant_id = ?",
                Integer.class,
                unknown))
        .isZero();
  }

  @Test
  @DisplayName("Only a coordinator of the university reads the uncovered demand")
  void onlyACoordinatorOfTheUniversityReadsTheUncoveredDemand() throws Exception {
    request(ana, DAY, DAY).andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  /** The clock at that hour of that day in Lima. */
  private void at(LocalDate day, int hour, int minute) {
    clock.set(day.atTime(hour, minute).atZone(LIMA).toInstant());
  }

  /** A search for a course, for the four hours that start a day after now. */
  private ResultActions searchFor(Person student, UUID course) throws Exception {
    Instant from = clock.instant().plus(Duration.ofDays(1));
    return mockMvc.perform(
        get("/api/v1/search/offers")
            .header("Authorization", "Bearer " + student.token())
            .param("catalogItemId", course.toString())
            .param("from", from.toString())
            .param("to", from.plus(Duration.ofHours(4)).toString()));
  }

  /** Waits until every search heard so far has been counted. */
  private void idle() throws InterruptedException {
    assertThat(queue.awaitIdle(PATIENCE)).as("the uncovered demand was counted in time").isTrue();
  }

  private String read(Person person, LocalDate from, LocalDate to) throws Exception {
    return request(person, from, to)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private ResultActions request(Person person, LocalDate from, LocalDate to) throws Exception {
    return mockMvc.perform(
        get("/api/v1/coordinator/indicators")
            .header("Authorization", "Bearer " + person.token())
            .param("from", from.toString())
            .param("to", to.toString()));
  }
}
