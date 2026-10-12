package pe.ayni.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.recognition.application.SessionRatings;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionView;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.SkillsApi;

/**
 * US30, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US30-recognition-session-evidence.feature},
 * with the same names. The identity of each person, the sessions, the bookings and the catalogue are
 * stubbed, since they are other modules; the request is submitted through the real endpoint of US28 and
 * everything else is the real application.
 *
 * <p>Each test works in a university of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecognitionSessionEvidenceAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final UUID coordinator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();
  private final UUID learner = UUID.randomUUID();
  private final UUID plainStudent = UUID.randomUUID();
  private final UUID databases = UUID.randomUUID();
  private final UUID figma = UUID.randomUUID();
  private final Map<UUID, BookingView> bookings = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> rated = new ConcurrentHashMap<>();

  @Autowired private MockMvc mockMvc;
  @Autowired private RecognitionRuleRepository rules;
  @MockitoBean private IdentityApi identity;
  @MockitoBean private SessionsApi sessions;
  @MockitoBean private BookingApi booking;
  @MockitoBean private SkillsApi skills;
  @MockitoBean private SessionRatings ratings;

  private String tenant;
  private String request;
  private List<SessionSummary> backing;

  /** Background: a coordinator and a student who submitted a request of a course session and a tool session. */
  @BeforeEach
  void aCoordinatorAndAStudentWhoSubmittedARequest() throws Exception {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    bookings.clear();
    rated.clear();
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              if (id.equals(coordinator)) {
                return new UserView(id, tenant, UserRole.COORDINATOR, "carla@upc.edu.pe", null, "Carla Rios", null, null, null);
              }
              if (id.equals(student)) {
                return new UserView(id, tenant, UserRole.STUDENT, "ana@upc.edu.pe", "U202310949", "Ana Torres", null, null, null);
              }
              if (id.equals(learner)) {
                return new UserView(id, tenant, UserRole.STUDENT, "luis@upc.edu.pe", "U202110001", "Luis Vega", null, null, null);
              }
              if (id.equals(plainStudent)) {
                return new UserView(id, tenant, UserRole.STUDENT, "marta@upc.edu.pe", "U202010002", "Marta Quispe", null, null, null);
              }
              throw new NoSuchElementException("user %s not found".formatted(id));
            });
    when(skills.requireItem(databases)).thenReturn(new CatalogItemView(databases, CatalogScope.UNIVERSITY, "Databases", "1ASI0616"));
    when(skills.requireItem(figma)).thenReturn(new CatalogItemView(figma, CatalogScope.GLOBAL, "Figma", null));
    when(booking.requireBooking(any(UUID.class))).thenAnswer(call -> bookings.get(call.<UUID>getArgument(0)));
    when(ratings.starsOf(any()))
        .thenAnswer(
            call -> {
              Map<UUID, Integer> found = new java.util.HashMap<>();
              for (UUID id : call.<java.util.Collection<UUID>>getArgument(0)) {
                if (rated.containsKey(id)) {
                  found.put(id, rated.get(id));
                }
              }
              return found;
            });
    rules.save(new RecognitionRule(UUID.randomUUID(), tenant, 20, null, LocalDate.of(2026, 1, 1), NOW));

    backing = new ArrayList<>();
    backing.add(session(student, databases, 12));
    backing.add(session(student, figma, 8));
    rated.put(backing.get(0).sessionId(), 5);
    when(sessions.completedSessionsOf(student)).thenReturn(List.copyOf(backing));
    request =
        mockMvc
            .perform(post("/api/v1/recognition/requests").header("X-Tenant-Id", tenant).header("X-User-Id", student.toString()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
  }

  private int counter;

  /** A completed session the student taught to the learner on the item, the first ones the oldest. */
  private SessionSummary session(UUID tutor, UUID item, int hours) {
    Instant start = NOW.minusSeconds(86400L * (40 - counter++)).plusSeconds(120);
    SessionSummary summary =
        new SessionSummary(UUID.randomUUID(), UUID.randomUUID(), learner, start, start.plusSeconds(3600L * hours + 300), hours);
    bookings.put(summary.bookingId(), new BookingView(summary.bookingId(), learner, tutor, item, start, start, hours, "need", BookingStatus.COMPLETED));
    when(sessions.requireSession(summary.sessionId()))
        .thenReturn(new SessionView(summary.sessionId(), summary.bookingId(), learner, tutor, start.minusSeconds(120), start.plusSeconds(3600L * hours), SessionStatus.COMPLETED));
    return summary;
  }

  private String requestId() {
    return JsonPath.read(request, "$.id");
  }

  private ResultActions support(String forTenant, UUID user) throws Exception {
    return mockMvc.perform(
        get("/api/v1/coordinator/recognition/requests/" + requestId() + "/sessions")
            .header("X-Tenant-Id", forTenant)
            .header("X-User-Id", user.toString()));
  }

  private ResultActions evidence(String forTenant, UUID user, UUID sessionId) throws Exception {
    return mockMvc.perform(
        get("/api/v1/coordinator/recognition/requests/" + requestId() + "/sessions/" + sessionId)
            .header("X-Tenant-Id", forTenant)
            .header("X-User-Id", user.toString()));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("Detail of the sessions that support it")
  void detailOfTheSessionsThatSupportIt() throws Exception {

    String answer = body(support(tenant, coordinator).andExpect(status().isOk()));

    assertThat(JsonPath.<List<String>>read(answer, "$.sessions[*].sessionId"))
        .containsExactly(backing.get(0).sessionId().toString(), backing.get(1).sessionId().toString());
    assertThat(JsonPath.<List<String>>read(answer, "$.sessions[*].startedAt")).hasSize(2).doesNotContainNull();
    assertThat(JsonPath.<List<Integer>>read(answer, "$.sessions[*].durationMinutes")).containsExactly(12 * 60 + 5, 8 * 60 + 5);
    assertThat(JsonPath.<List<String>>read(answer, "$.sessions[*].taught")).containsExactly("Databases", "Figma");
    assertThat(JsonPath.<List<String>>read(answer, "$.sessions[*].taughtKind")).containsExactly("COURSE", "TOOL");
    assertThat(JsonPath.<String>read(answer, "$.sessions[0].courseCode")).isEqualTo("1ASI0616");
    assertThat(JsonPath.<Object>read(answer, "$.sessions[1].courseCode")).isNull();
  }

  @Test
  @DisplayName("Evidence of each session")
  void evidenceOfEachSession() throws Exception {

    String answer = body(evidence(tenant, coordinator, backing.get(0).sessionId()).andExpect(status().isOk()));

    assertThat(JsonPath.<List<String>>read(answer, "$.attendance[*].role")).containsExactly("TUTOR", "STUDENT");
    assertThat(JsonPath.<List<String>>read(answer, "$.attendance[*].name")).containsExactly("Ana Torres", "Luis Vega");
    assertThat(JsonPath.<List<Boolean>>read(answer, "$.attendance[*].presenceVerified")).containsExactly(true, true);
    assertThat(JsonPath.<Integer>read(answer, "$.tutorStars")).isEqualTo(5);
    assertThat(JsonPath.<String>read(answer, "$.taught.name")).isEqualTo("Databases");
    assertThat(JsonPath.<Integer>read(answer, "$.hours")).isEqualTo(12);
    String unrated = body(evidence(tenant, coordinator, backing.get(1).sessionId()).andExpect(status().isOk()));
    assertThat(JsonPath.<Object>read(unrated, "$.tutorStars")).isNull();
    assertThat(JsonPath.<String>read(unrated, "$.taught.kind")).isEqualTo("TOOL");
  }

  @Test
  @DisplayName("Consistency between the detail and the total")
  void consistencyBetweenTheDetailAndTheTotal() throws Exception {

    String answer = body(support(tenant, coordinator).andExpect(status().isOk()));

    int listed = JsonPath.<List<Integer>>read(answer, "$.sessions[*].hours").stream().mapToInt(Integer::intValue).sum();
    assertThat(listed).isEqualTo(JsonPath.<Integer>read(answer, "$.totalHours")).isEqualTo(20);
    assertThat(JsonPath.<Integer>read(answer, "$.listedHours")).isEqualTo(20);
    assertThat(JsonPath.<Integer>read(request, "$.totalHours")).isEqualTo(20);
  }

  @Test
  @DisplayName("Restricted access")
  void restrictedAccess() throws Exception {

    support(tenant, plainStudent).andExpect(status().isForbidden());
    support(tenant, student).andExpect(status().isForbidden());
    evidence(tenant, plainStudent, backing.get(0).sessionId()).andExpect(status().isForbidden());
    evidence(tenant, student, backing.get(0).sessionId()).andExpect(status().isForbidden());
    support(tenant, UUID.randomUUID()).andExpect(status().isNotFound());
    evidence(tenant, UUID.randomUUID(), backing.get(0).sessionId()).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A session that does not back the request")
  void aSessionThatDoesNotBackTheRequest() throws Exception {

    SessionSummary extra = session(plainStudent, databases, 20);
    when(sessions.completedSessionsOf(plainStudent)).thenReturn(List.of(extra));
    mockMvc
        .perform(post("/api/v1/recognition/requests").header("X-Tenant-Id", tenant).header("X-User-Id", plainStudent.toString()))
        .andExpect(status().isCreated());

    evidence(tenant, coordinator, extra.sessionId()).andExpect(status().isNotFound());
    evidence(tenant, coordinator, UUID.randomUUID()).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A request of another university")
  void aRequestOfAnotherUniversity() throws Exception {

    String other = "O" + tenant;
    when(identity.requireUser(coordinator))
        .thenReturn(new UserView(coordinator, other, UserRole.COORDINATOR, "carla@u.pe", null, "Carla Rios", null, null, null));

    support(other, coordinator).andExpect(status().isNotFound());
    evidence(other, coordinator, backing.get(0).sessionId()).andExpect(status().isNotFound());
  }
}
