package pe.ayni.matching;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.reputation.ReputationApi;
import pe.ayni.reputation.TutorStandingView;
import pe.ayni.shared.events.HoursGenerated;
import pe.ayni.skills.SkillsApi;

/**
 * US01 over HTTP against a real PostgreSQL, one test per scenario of {@code
 * features/US01-search-offers.feature}, plus the refusals the endpoint owes an honest answer.
 *
 * <p>The projection is filled the way it is in the application: booking's {@code HoursGenerated}
 * is published and matching hears it. Skills, identity and reputation are the seams, mocked with
 * the answers the real modules give. Every test searches a course of its own, so no test sees
 * another one's offers.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchOffersAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = MatchingTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final ZoneId LIMA = ZoneId.of("America/Lima");

  private final UUID course = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @MockitoBean private SkillsApi skills;
  @MockitoBean private IdentityApi identity;
  @MockitoBean private ReputationApi reputation;

  @BeforeEach
  void aUniversityInLima() {
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", LIMA.getId(), new BigDecimal("13.00"), true));
    when(identity.requireTenant("NOPE"))
        .thenThrow(new NoSuchElementException("Tenant not found: NOPE"));
  }

  /** Tomorrow at that hour, Lima time, as the instant the API speaks in. */
  private static Instant tomorrowAt(int hour) {
    LocalDate tomorrow = LocalDate.now(LIMA).plusDays(1);
    return ZonedDateTime.of(tomorrow, LocalTime.of(hour, 0), LIMA).toInstant();
  }

  /**
   * A tutor of the course, with that average, whose hours booking just generated.
   *
   * @param averageStars {@code null} for a tutor with too few ratings to show one
   */
  private UUID tutorFreeAt(String tenant, String name, String averageStars, Instant... starts) {
    UUID tutor = UUID.randomUUID();
    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of(course));
    when(identity.requireUser(tutor))
        .thenReturn(
            new UserView(
                tutor, tenant, UserRole.STUDENT, tutor + "@upc.edu.pe", "U" + tutor, name, null,
                null, null));
    when(reputation.standingOf(eq(tutor), any()))
        .thenReturn(
            Optional.of(
                averageStars == null
                    ? new TutorStandingView(tutor, course, 1, 1, null)
                    : new TutorStandingView(tutor, course, 7, 5, new BigDecimal(averageStars))));

    List<HoursGenerated.Block> blocks =
        Arrays.stream(starts)
            .map(start -> new HoursGenerated.Block(UUID.randomUUID(), start))
            .toList();
    transactions.executeWithoutResult(
        status ->
            events.publishEvent(new HoursGenerated(tenant, tutor, blocks, Instant.now())));
    return tutor;
  }

  private UUID tutorFreeAt(String name, String averageStars, Instant... starts) {
    return tutorFreeAt(UPC, name, averageStars, starts);
  }

  private ResultActions search(Instant from, Instant to) throws Exception {
    return mockMvc.perform(
        get("/api/v1/search/offers")
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", student)
            .param("catalogItemId", course.toString())
            .param("from", from.toString())
            .param("to", to.toString()));
  }

  /** Tomorrow afternoon, from two to six, Lima time. */
  private ResultActions searchTomorrowAfternoon() throws Exception {
    return search(tomorrowAt(14), tomorrowAt(18));
  }

  @Test
  @DisplayName("Search with results")
  void searchWithResults() throws Exception {

    UUID bruno = tutorFreeAt("Bruno Salas", "4.50", tomorrowAt(15), tomorrowAt(16));

    searchTomorrowAfternoon()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(true))
        .andExpect(jsonPath("$.offers", hasSize(2)))
        .andExpect(jsonPath("$.offers[0].tutorId").value(bruno.toString()))
        .andExpect(jsonPath("$.offers[0].tutorName").value("Bruno Salas"))
        .andExpect(jsonPath("$.offers[0].catalogItemId").value(course.toString()))
        .andExpect(jsonPath("$.offers[0].blockId").isNotEmpty())
        .andExpect(jsonPath("$.offers[0].startsAt").value(tomorrowAt(15).toString()))
        .andExpect(jsonPath("$.offers[0].endsAt").value(tomorrowAt(16).toString()))
        .andExpect(jsonPath("$.offers[0].averageStars").value(4.50))
        .andExpect(jsonPath("$.offers[0].ratingsCount").value(5))
        .andExpect(jsonPath("$.offers[0].sessionsTaught").value(7))
        .andExpect(jsonPath("$.offers[0].newTutor").value(false))
        .andExpect(jsonPath("$.offers[1].startsAt").value(tomorrowAt(16).toString()))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("Several tutors at the same hour")
  void severalTutorsAtTheSameHour() throws Exception {

    UUID newcomer = tutorFreeAt("Aaron Vega", null, tomorrowAt(15));
    UUID good = tutorFreeAt("Zoe Mori", "4.20", tomorrowAt(15));
    UUID best = tutorFreeAt("Mia Rojas", "4.90", tomorrowAt(15));

    searchTomorrowAfternoon()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(true))
        .andExpect(jsonPath("$.offers", hasSize(3)))
        .andExpect(jsonPath("$.offers[0].tutorId").value(best.toString()))
        .andExpect(jsonPath("$.offers[0].averageStars").value(4.90))
        .andExpect(jsonPath("$.offers[1].tutorId").value(good.toString()))
        .andExpect(jsonPath("$.offers[1].averageStars").value(4.20))
        .andExpect(jsonPath("$.offers[2].tutorId").value(newcomer.toString()))
        .andExpect(jsonPath("$.offers[2].averageStars").value(nullValue()))
        .andExpect(jsonPath("$.offers[2].ratingsCount").value(1))
        .andExpect(jsonPath("$.offers[2].newTutor").value(true));
  }

  @Test
  @DisplayName("No availability in the chosen window")
  void noAvailabilityInTheChosenWindow() throws Exception {

    tutorFreeAt("Bruno Salas", "4.50", tomorrowAt(9), tomorrowAt(20), tomorrowAt(22));

    searchTomorrowAfternoon()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(false))
        .andExpect(jsonPath("$.offers", hasSize(3)))
        .andExpect(jsonPath("$.offers[0].startsAt").value(tomorrowAt(9).toString()))
        .andExpect(jsonPath("$.offers[1].startsAt").value(tomorrowAt(20).toString()))
        .andExpect(jsonPath("$.offers[2].startsAt").value(tomorrowAt(22).toString()));

    // Only the closest ones when there are more than a page: eight in the evening is two hours
    // away, ten at night four, nine in the morning five.
    mockMvc
        .perform(
            get("/api/v1/search/offers")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student)
                .param("catalogItemId", course.toString())
                .param("from", tomorrowAt(14).toString())
                .param("to", tomorrowAt(18).toString())
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(false))
        .andExpect(jsonPath("$.offers", hasSize(2)))
        .andExpect(jsonPath("$.offers[0].startsAt").value(tomorrowAt(20).toString()))
        .andExpect(jsonPath("$.offers[1].startsAt").value(tomorrowAt(22).toString()));
  }

  @Test
  @DisplayName("No tutor at all for the course")
  void noTutorAtAll() throws Exception {

    searchTomorrowAfternoon()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(false))
        .andExpect(jsonPath("$.offers", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("Times are stored in UTC and shown in the university's time zone")
  void timesAreStoredInUtcAndShownInTheUniversitysTimeZone() throws Exception {

    // Eight at night in Lima is already the next day in UTC.
    tutorFreeAt("Bruno Salas", "4.50", tomorrowAt(20));

    search(tomorrowAt(19), tomorrowAt(21))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("America/Lima"))
        .andExpect(jsonPath("$.offers[0].startsAt").value(tomorrowAt(20).toString()))
        .andExpect(jsonPath("$.offers[0].startsAt", containsString("T01:00:00Z")));
  }

  @Test
  @DisplayName("Hours that have already started are not offered")
  void hoursThatStartedAreNotOffered() throws Exception {

    Instant thisHour = Instant.now().truncatedTo(ChronoUnit.HOURS);
    tutorFreeAt(
        "Bruno Salas",
        "4.50",
        thisHour.minus(Duration.ofHours(1)),
        thisHour,
        thisHour.plus(Duration.ofHours(1)));

    search(thisHour.minus(Duration.ofHours(2)), thisHour.plus(Duration.ofHours(3)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(true))
        .andExpect(jsonPath("$.offers", hasSize(1)))
        .andExpect(
            jsonPath("$.offers[0].startsAt").value(thisHour.plus(Duration.ofHours(1)).toString()));
  }

  @Test
  @DisplayName("Only the offers of the student's university")
  void onlyTheOffersOfTheStudentsUniversity() throws Exception {

    tutorFreeAt("PUCP", "Somebody Else", "5.00", tomorrowAt(15));
    UUID bruno = tutorFreeAt("Bruno Salas", "4.50", tomorrowAt(16));

    searchTomorrowAfternoon()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offers", hasSize(1)))
        .andExpect(jsonPath("$.offers[0].tutorId").value(bruno.toString()));
  }

  @Test
  @DisplayName("A tutor searching the course does not find their own hours")
  void aTutorDoesNotFindTheirOwnHours() throws Exception {

    UUID me = tutorFreeAt("Me Myself", "4.50", tomorrowAt(15));
    UUID other = tutorFreeAt("Bruno Salas", "4.00", tomorrowAt(15));

    mockMvc
        .perform(
            get("/api/v1/search/offers")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", me)
                .param("catalogItemId", course.toString())
                .param("from", tomorrowAt(14).toString())
                .param("to", tomorrowAt(18).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offers", hasSize(1)))
        .andExpect(jsonPath("$.offers[0].tutorId").value(other.toString()));
  }

  @Test
  @DisplayName("A long list comes in pages")
  void aLongListComesInPages() throws Exception {

    tutorFreeAt("Bruno Salas", "4.50", tomorrowAt(14), tomorrowAt(15), tomorrowAt(16));

    mockMvc
        .perform(
            get("/api/v1/search/offers")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student)
                .param("catalogItemId", course.toString())
                .param("from", tomorrowAt(14).toString())
                .param("to", tomorrowAt(18).toString())
                .param("page", "1")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exactMatch").value(true))
        .andExpect(jsonPath("$.offers", hasSize(1)))
        .andExpect(jsonPath("$.offers[0].startsAt").value(tomorrowAt(16).toString()))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  @DisplayName("A window that does not end after it starts is refused in the common error shape")
  void aBackwardsWindowIsRefused() throws Exception {

    search(tomorrowAt(18), tomorrowAt(14))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("from must be before to"))
        .andExpect(jsonPath("$.path").value("/api/v1/search/offers"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("A search without a course, or with a date that is not ISO 8601, is refused")
  void anIncompleteSearchIsRefused() throws Exception {

    mockMvc
        .perform(
            get("/api/v1/search/offers")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student)
                .param("from", tomorrowAt(14).toString())
                .param("to", tomorrowAt(18).toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The parameter catalogItemId is required"));

    mockMvc
        .perform(
            get("/api/v1/search/offers")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student)
                .param("catalogItemId", course.toString())
                .param("from", "tomorrow")
                .param("to", tomorrowAt(18).toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("from could not be read")));
  }

  @Test
  @DisplayName("A page size out of range is refused")
  void aPageSizeOutOfRangeIsRefused() throws Exception {

    mockMvc
        .perform(
            get("/api/v1/search/offers")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student)
                .param("catalogItemId", course.toString())
                .param("from", tomorrowAt(14).toString())
                .param("to", tomorrowAt(18).toString())
                .param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.path").value("/api/v1/search/offers"));
  }

  @Test
  @DisplayName("A search without a university or without a student is refused")
  void aSearchWithoutHeadersIsRefused() throws Exception {

    mockMvc
        .perform(
            get("/api/v1/search/offers")
                .header("X-User-Id", student)
                .param("catalogItemId", course.toString())
                .param("from", tomorrowAt(14).toString())
                .param("to", tomorrowAt(18).toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("X-Tenant-Id")));

    mockMvc
        .perform(
            get("/api/v1/search/offers")
                .header("X-Tenant-Id", UPC)
                .param("catalogItemId", course.toString())
                .param("from", tomorrowAt(14).toString())
                .param("to", tomorrowAt(18).toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("X-User-Id")));
  }

  @Test
  @DisplayName("A university that does not exist is not found")
  void anUnknownUniversityIsNotFound() throws Exception {

    mockMvc
        .perform(
            get("/api/v1/search/offers")
                .header("X-Tenant-Id", "NOPE")
                .header("X-User-Id", student)
                .param("catalogItemId", course.toString())
                .param("from", tomorrowAt(14).toString())
                .param("to", tomorrowAt(18).toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Tenant not found: NOPE"));
  }
}
