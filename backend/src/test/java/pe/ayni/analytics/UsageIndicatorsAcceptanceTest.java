package pe.ayni.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.analytics.application.ProjectUsageUseCase;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US53, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US53-usage-indicators.feature},
 * with the same names. Every figure is produced by publishing the real events inside a
 * transaction, as the module that owns them would, and never by inserting rows. Identity is another
 * module, so what it would say is stubbed. Each test works in a university of its own.
 *
 * <p>The university's time zone is Lima, five hours behind UTC, so an evening in Lima is already
 * the next day in UTC and the tests can tell the two days apart.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UsageIndicatorsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = AnalyticsTestDatabase.INSTANCE;

  private final UUID coordinator = UUID.randomUUID();
  private final UUID otherUniversityCoordinator = UUID.randomUUID();
  private final UUID restricted = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();
  private final UUID tutorBruno = UUID.randomUUID();
  private final UUID tutorCarla = UUID.randomUUID();
  private final UUID studentDiego = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private ProjectUsageUseCase projection;
  @MockitoBean private IdentityApi identity;

  private String tenant;
  private String otherTenant;

  /** Background: a university in Lima with its coordinator, and another one. */
  @BeforeEach
  void aUniversityWithItsPeople() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    otherTenant = "O" + UUID.randomUUID().toString().substring(0, 8);
    when(identity.requireTenant(any(String.class)))
        .thenAnswer(
            call -> {
              String code = call.getArgument(0);
              if (code.equals(tenant) || code.equals(otherTenant)) {
                return new TenantView(code, "University", "America/Lima", BigDecimal.TEN, true);
              }
              throw new NoSuchElementException("tenant %s not found".formatted(code));
            });
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              if (id.equals(coordinator) || id.equals(restricted)) {
                return new UserView(id, tenant, UserRole.COORDINATOR, "carla@upc.edu.pe", null, "Carla Rios", null, null, null);
              }
              if (id.equals(otherUniversityCoordinator)) {
                return new UserView(id, otherTenant, UserRole.COORDINATOR, "olga@other.edu.pe", null, "Olga Diaz", null, null, null);
              }
              if (id.equals(student)) {
                return new UserView(id, tenant, UserRole.STUDENT, "ana@upc.edu.pe", "U2023", "Ana Paredes", null, null, null);
              }
              throw new NoSuchElementException("user %s not found".formatted(id));
            });
    when(identity.isActive(any(UUID.class))).thenAnswer(call -> !call.getArgument(0).equals(restricted));
    when(identity.currentPolicy(any(String.class), any())).thenReturn(Optional.empty());
  }

  // ---------------------------------------------------------------------------------------------
  // The scenarios of the story
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("General indicators")
  void generalIndicators() throws Exception {
    // Monday 5 October: two sessions, one of 2 hours and one of 1, sharing the tutor.
    completed(tenant, tutorBruno, student, 2, "2026-10-05T15:00:00Z");
    completed(tenant, tutorBruno, studentDiego, 1, "2026-10-05T18:00:00Z");
    // Tuesday 6 October in Lima, although the clock in UTC is already Wednesday the 7th.
    completed(tenant, tutorCarla, student, 3, "2026-10-07T03:30:00Z");
    // Students that joined, and recognitions: one granted, one refused.
    activated(tenant, UUID.randomUUID(), "2026-10-05T14:00:00Z");
    activated(tenant, UUID.randomUUID(), "2026-10-06T14:00:00Z");
    resolved(tenant, true, "2026-10-06T16:00:00Z");
    resolved(tenant, false, "2026-10-06T16:30:00Z");
    // Outside the period.
    completed(tenant, tutorBruno, student, 5, "2026-10-20T15:00:00Z");

    String body = read(tenant, coordinator, "2026-10-05", "2026-10-08");

    assertThat(JsonPath.<String>read(body, "$.from")).isEqualTo("2026-10-05");
    assertThat(JsonPath.<String>read(body, "$.to")).isEqualTo("2026-10-08");
    assertThat(JsonPath.<Integer>read(body, "$.totals.sessionsCompleted")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(body, "$.totals.hoursExchanged")).isEqualTo(6);
    assertThat(JsonPath.<Integer>read(body, "$.totals.newStudents")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(body, "$.totals.recognitionsGranted")).isEqualTo(1);
    // Four different people took part, and the student of two sessions counts once.
    assertThat(JsonPath.<Integer>read(body, "$.totals.activeStudents")).isEqualTo(4);

    // One entry per day, also the days with nothing.
    assertThat(JsonPath.<List<String>>read(body, "$.days[*].day"))
        .containsExactly("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08");
    assertThat(JsonPath.<Integer>read(body, "$.days[0].figures.sessionsCompleted")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(body, "$.days[0].figures.hoursExchanged")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(body, "$.days[0].figures.activeStudents")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(body, "$.days[0].figures.newStudents")).isEqualTo(1);
    // The evening in Lima is the sixth, not the seventh that UTC says.
    assertThat(JsonPath.<Integer>read(body, "$.days[1].figures.sessionsCompleted")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(body, "$.days[1].figures.hoursExchanged")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(body, "$.days[1].figures.activeStudents")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(body, "$.days[1].figures.newStudents")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(body, "$.days[1].figures.recognitionsGranted")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(body, "$.days[2].figures.sessionsCompleted")).isZero();
    assertThat(JsonPath.<Integer>read(body, "$.days[3].figures.hoursExchanged")).isZero();

    // Counts only: no person, not even an identifier, is in the answer.
    assertThat(body).doesNotContain(student.toString(), tutorBruno.toString(), tutorCarla.toString());
    assertThat(body).doesNotContain("@");
  }

  @Test
  @DisplayName("Indicators limited to my institution")
  void indicatorsLimitedToMyInstitution() throws Exception {
    completed(tenant, tutorBruno, student, 2, "2026-10-05T15:00:00Z");
    activated(tenant, UUID.randomUUID(), "2026-10-05T14:00:00Z");
    // The same people and the same day, in another university.
    completed(otherTenant, tutorBruno, student, 4, "2026-10-05T15:00:00Z");
    completed(otherTenant, tutorCarla, studentDiego, 1, "2026-10-05T16:00:00Z");
    resolved(otherTenant, true, "2026-10-05T17:00:00Z");

    String mine = read(tenant, coordinator, "2026-10-05", "2026-10-05");
    String theirs = read(otherTenant, otherUniversityCoordinator, "2026-10-05", "2026-10-05");

    assertThat(JsonPath.<Integer>read(mine, "$.totals.sessionsCompleted")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(mine, "$.totals.hoursExchanged")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(mine, "$.totals.activeStudents")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(mine, "$.totals.newStudents")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(mine, "$.totals.recognitionsGranted")).isZero();
    assertThat(JsonPath.<Integer>read(theirs, "$.totals.sessionsCompleted")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(theirs, "$.totals.hoursExchanged")).isEqualTo(5);
    assertThat(JsonPath.<Integer>read(theirs, "$.totals.activeStudents")).isEqualTo(4);
    assertThat(JsonPath.<Integer>read(theirs, "$.totals.recognitionsGranted")).isEqualTo(1);

    // A coordinator of another university cannot read this one by naming it.
    request(tenant, otherUniversityCoordinator, "/api/v1/coordinator/indicators", "2026-10-05", "2026-10-05")
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Exporting the indicators")
  void exportingTheIndicators() throws Exception {
    completed(tenant, tutorBruno, student, 2, "2026-10-05T15:00:00Z");
    completed(tenant, tutorBruno, student, 1, "2026-10-06T15:00:00Z");
    activated(tenant, UUID.randomUUID(), "2026-10-06T14:00:00Z");
    resolved(tenant, true, "2026-10-06T16:00:00Z");
    completed(otherTenant, tutorCarla, studentDiego, 9, "2026-10-05T15:00:00Z");

    MvcResult result =
        request(tenant, coordinator, "/api/v1/coordinator/indicators/export", "2026-10-05", "2026-10-07")
            .andExpect(status().isOk())
            .andReturn();

    String disposition = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
    assertThat(disposition).startsWith("attachment").contains("usage-indicators-2026-10-05-2026-10-07.csv");
    assertThat(result.getResponse().getContentType()).startsWith("text/csv");
    assertThat(result.getResponse().getContentAsString())
        .isEqualTo(
            """
            day,sessions_completed,hours_exchanged,active_students,new_students,recognitions_granted\r
            2026-10-05,1,2,2,0,0\r
            2026-10-06,1,1,2,1,1\r
            2026-10-07,0,0,0,0,0\r
            TOTAL,2,3,2,1,1\r
            """);
  }

  @Test
  @DisplayName("A repeated event is counted once")
  void aRepeatedEventIsCountedOnce() throws Exception {
    SessionCompleted session = sessionCompleted(tenant, tutorBruno, student, 2, "2026-10-05T15:00:00Z");
    UUID newStudent = UUID.randomUUID();
    RecognitionResolved granted = recognition(tenant, true, "2026-10-05T16:00:00Z");

    for (int delivery = 0; delivery < 2; delivery++) {
      publish(session);
      publish(new StudentActivated(tenant, newStudent, "new@upc.edu.pe", "U2030", Instant.parse("2026-10-05T14:00:00Z")));
      publish(granted);
    }

    String body = read(tenant, coordinator, "2026-10-05", "2026-10-05");
    assertThat(JsonPath.<Integer>read(body, "$.totals.sessionsCompleted")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(body, "$.totals.hoursExchanged")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(body, "$.totals.activeStudents")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(body, "$.totals.newStudents")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(body, "$.totals.recognitionsGranted")).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------------------------
  // What the projection promises, beyond the scenarios
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("The same event delivered at the same moment is counted once")
  void theSameEventDeliveredAtTheSameMomentIsCountedOnce() throws Exception {
    SessionCompleted session = sessionCompleted(tenant, tutorBruno, student, 2, "2026-10-05T15:00:00Z");
    CyclicBarrier barrier = new CyclicBarrier(2);
    AtomicInteger counted = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<?>> deliveries =
          List.of(
              pool.submit(() -> deliver(barrier, session, counted)),
              pool.submit(() -> deliver(barrier, session, counted)));
      for (Future<?> delivery : deliveries) {
        delivery.get();
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(counted).hasValue(1);
    String body = read(tenant, coordinator, "2026-10-05", "2026-10-05");
    assertThat(JsonPath.<Integer>read(body, "$.totals.sessionsCompleted")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(body, "$.totals.hoursExchanged")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(body, "$.totals.activeStudents")).isEqualTo(2);
  }

  @Test
  @DisplayName("A student with several sessions in a day is one active student")
  void aStudentWithSeveralSessionsInADayIsOneActiveStudent() throws Exception {
    completed(tenant, tutorBruno, student, 1, "2026-10-05T14:00:00Z");
    completed(tenant, tutorCarla, student, 1, "2026-10-05T16:00:00Z");
    completed(tenant, tutorBruno, student, 1, "2026-10-06T14:00:00Z");

    String body = read(tenant, coordinator, "2026-10-05", "2026-10-06");

    assertThat(JsonPath.<Integer>read(body, "$.days[0].figures.activeStudents")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(body, "$.days[1].figures.activeStudents")).isEqualTo(2);
    // Five appearances over two days, three different people.
    assertThat(JsonPath.<Integer>read(body, "$.totals.activeStudents")).isEqualTo(3);
  }

  @Test
  @DisplayName("Nothing about a student's email or code is kept")
  void nothingAboutAStudentsEmailOrCodeIsKept() {
    UUID newStudent = UUID.randomUUID();
    publish(new StudentActivated(tenant, newStudent, "secret.address@upc.edu.pe", "U20301234", Instant.parse("2026-10-05T14:00:00Z")));

    List<String> everything =
        jdbc.queryForList(
            """
            select row_to_json(t)::text from analytics.daily_indicators t where tenant_id = ?
            union all select row_to_json(t)::text from analytics.counted_events t where tenant_id = ?
            union all select row_to_json(t)::text from analytics.active_student_days t where tenant_id = ?
            """,
            String.class,
            tenant,
            tenant,
            tenant);

    assertThat(everything).isNotEmpty();
    assertThat(String.join(" ", everything)).doesNotContain("secret.address", "U20301234");
  }

  @Test
  @DisplayName("A projection that cannot be done does not break who published")
  void aProjectionThatCannotBeDoneDoesNotBreakWhoPublished() {
    String unknown = "X" + UUID.randomUUID().toString().substring(0, 8);

    assertThatCode(
            () -> {
              publish(sessionCompleted(unknown, tutorBruno, student, 2, "2026-10-05T15:00:00Z"));
              publish(new StudentActivated(unknown, UUID.randomUUID(), "x@x.pe", "U1", Instant.now()));
              publish(recognition(unknown, true, "2026-10-05T16:00:00Z"));
            })
        .doesNotThrowAnyException();

    assertThat(
            jdbc.queryForObject(
                "select count(*) from analytics.daily_indicators where tenant_id = ?",
                Integer.class,
                unknown))
        .isZero();
  }

  @Test
  @DisplayName("Only an active coordinator of the university reads the indicators")
  void onlyAnActiveCoordinatorOfTheUniversityReadsTheIndicators() throws Exception {
    for (String path : List.of("/api/v1/coordinator/indicators", "/api/v1/coordinator/indicators/export")) {
      // A student, a coordinator who is restricted, and somebody the university does not know.
      request(tenant, student, path, "2026-10-05", "2026-10-06").andExpect(status().isForbidden());
      request(tenant, restricted, path, "2026-10-05", "2026-10-06").andExpect(status().isForbidden());
      request(tenant, UUID.randomUUID(), path, "2026-10-05", "2026-10-06").andExpect(status().isForbidden());
      request(tenant, coordinator, path, "2026-10-05", "2026-10-06").andExpect(status().isOk());
    }
  }

  @Test
  @DisplayName("A period that is inverted, too long or unreadable is refused")
  void aPeriodThatIsInvertedTooLongOrUnreadableIsRefused() throws Exception {
    String path = "/api/v1/coordinator/indicators";
    for (String route : List.of(path, path + "/export")) {
      request(tenant, coordinator, route, "2026-10-06", "2026-10-05").andExpect(status().isBadRequest());
      request(tenant, coordinator, route, "2026-01-01", "2027-01-02").andExpect(status().isBadRequest());
      request(tenant, coordinator, route, "yesterday", "2026-10-05").andExpect(status().isBadRequest());
      mockMvc
          .perform(get(route).header("X-Tenant-Id", tenant).header("X-User-Id", coordinator.toString()).param("from", "2026-10-05"))
          .andExpect(status().isBadRequest());
    }
    // A whole year is allowed.
    request(tenant, coordinator, path, "2026-01-01", "2026-12-31").andExpect(status().isOk());
    // A request with no university or no person is malformed, not forbidden.
    mockMvc
        .perform(get(path).header("X-User-Id", coordinator.toString()).param("from", "2026-10-05").param("to", "2026-10-06"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(path).header("X-Tenant-Id", tenant).param("from", "2026-10-05").param("to", "2026-10-06"))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private void deliver(CyclicBarrier barrier, SessionCompleted event, AtomicInteger counted) {
    try {
      barrier.await();
      TenantContext.runAs(event.tenantId(), () -> {
        if (projection.sessionCompleted(event)) {
          counted.incrementAndGet();
        }
      });
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private SessionCompleted sessionCompleted(String university, UUID tutor, UUID learner, int hours, String at) {
    return new SessionCompleted(
        university, UUID.randomUUID(), UUID.randomUUID(), tutor, learner, UUID.randomUUID(),
        Credits.of(hours), Instant.parse(at));
  }

  private void completed(String university, UUID tutor, UUID learner, int hours, String at) {
    publish(sessionCompleted(university, tutor, learner, hours, at));
  }

  private void activated(String university, UUID userId, String at) {
    publish(new StudentActivated(university, userId, "new@upc.edu.pe", "U2030", Instant.parse(at)));
  }

  private RecognitionResolved recognition(String university, boolean approved, String at) {
    return new RecognitionResolved(
        university, UUID.randomUUID(), UUID.randomUUID(), approved, "Reviewed", Instant.parse(at));
  }

  private void resolved(String university, boolean approved, String at) {
    publish(recognition(university, approved, at));
  }

  /** As the publishing module does: inside its transaction, so the listeners hear it on commit. */
  private void publish(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  private String read(String university, UUID person, String from, String to) throws Exception {
    return request(university, person, "/api/v1/coordinator/indicators", from, to)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private ResultActions request(String university, UUID person, String path, String from, String to)
      throws Exception {
    return mockMvc.perform(
        get(path)
            .header("X-Tenant-Id", university)
            .header("X-User-Id", person.toString())
            .param("from", from)
            .param("to", to));
  }
}
