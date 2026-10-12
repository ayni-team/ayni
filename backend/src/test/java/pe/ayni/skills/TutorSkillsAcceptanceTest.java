package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;

/**
 * US18, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US18-tutor-skills.feature}, with
 * the same names. The scenarios tagged {@code @pending} there have no test here, and say why.
 *
 * <p>The withdrawal is proven from what it publishes: {@code SkillWithdrawn} is the only thing that
 * takes a course out of the search, so that is the observable effect of this module.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class TutorSkillsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final BigDecimal THRESHOLD = new BigDecimal("13.00");

  /** A tutor of their own, so that one scenario cannot see another one's skills. */
  private final UUID tutor = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private CategoryRepository categories;
  @Autowired private ApplicationEvents applicationEvents;
  @MockitoBean private IdentityApi identity;

  /** Background: the university's minimum teaching grade is 13.00, for every scenario. */
  @BeforeEach
  void theUniversityAsksForThirteen() {
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", "America/Lima", THRESHOLD, true));
  }

  @Test
  @DisplayName("Listing of offered skills")
  void listingOfOfferedSkills() throws Exception {

    CatalogItem architecture = seedCourse("Architecture");
    CatalogItem databases = seedCourse("Databases");
    approved(tutor, architecture, "15.50");
    approved(tutor, databases, "16.00");
    offer(tutor, architecture);
    String databasesSkillId = offer(tutor, databases);
    withdraw(tutor, databasesSkillId).andExpect(status().isNoContent());

    String body = skillsOf(tutor);

    assertThat(read(body, "$[*].name", List.class)).containsExactly("Architecture", "Databases");
    assertThat(read(body, "$[*].status", List.class)).containsExactly("ENABLED", "WITHDRAWN");
    assertThat(read(body, "$[*].accreditationPath", List.class))
        .containsExactly("ACADEMIC_RECORD", "ACADEMIC_RECORD");
    assertThat(read(body, "$[*].statusChangedAt", List.class)).doesNotContainNull().hasSize(2);
  }

  @Test
  @DisplayName("Stop offering a skill")
  void stopOfferingASkill() throws Exception {

    CatalogItem course = seedCourse("Architecture");
    approved(tutor, course, "15.50");
    String skillId = offer(tutor, course);

    withdraw(tutor, skillId).andExpect(status().isNoContent());

    assertThat(read(skillsOf(tutor), "$[0].status", String.class)).isEqualTo("WITHDRAWN");
    assertThat(withdrawnEventsOf(course))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.tenantId()).isEqualTo(UPC);
              assertThat(event.tutorId()).isEqualTo(tutor);
            });
  }

  @Test
  @DisplayName("A withdrawn skill cannot be withdrawn again")
  void aWithdrawnSkillCannotBeWithdrawnAgain() throws Exception {

    CatalogItem course = seedCourse("Architecture");
    approved(tutor, course, "15.50");
    String skillId = offer(tutor, course);
    withdraw(tutor, skillId).andExpect(status().isNoContent());

    withdraw(tutor, skillId).andExpect(status().isConflict());

    assertThat(withdrawnEventsOf(course)).hasSize(1);
  }

  @Test
  @DisplayName("Another tutor's skill cannot be withdrawn")
  void anotherTutorsSkillCannotBeWithdrawn() throws Exception {

    UUID anotherTutor = UUID.randomUUID();
    CatalogItem course = seedCourse("Architecture");
    approved(anotherTutor, course, "15.50");
    String skillId = offer(anotherTutor, course);

    withdraw(tutor, skillId).andExpect(status().isForbidden());

    assertThat(read(skillsOf(anotherTutor), "$[0].status", String.class)).isEqualTo("ENABLED");
    assertThat(withdrawnEventsOf(course)).isEmpty();
  }

  @Test
  @DisplayName("A skill that does not exist is not found")
  void aSkillThatDoesNotExistIsNotFound() throws Exception {

    withdraw(tutor, UUID.randomUUID().toString()).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Offering a withdrawn course again")
  void offeringAWithdrawnCourseAgain() throws Exception {

    CatalogItem course = seedCourse("Architecture");
    approved(tutor, course, "15.50");
    String firstSkillId = offer(tutor, course);
    withdraw(tutor, firstSkillId).andExpect(status().isNoContent());

    String suggestions =
        mockMvc
            .perform(get("/api/v1/tutor/skills/suggestions").headers(headers(tutor)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(read(suggestions, "$[*].item.id", List.class)).contains(course.getId().toString());

    String secondSkillId = offer(tutor, course);

    assertThat(secondSkillId).isEqualTo(firstSkillId);
    String body = skillsOf(tutor);
    assertThat(read(body, "$", List.class)).hasSize(1);
    assertThat(read(body, "$[0].status", String.class)).isEqualTo("ENABLED");
  }

  private HttpHeaders headers(UUID user) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", UPC);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private ResultActions withdraw(UUID user, String skillId)
      throws Exception {
    return mockMvc.perform(delete("/api/v1/tutor/skills/" + skillId).headers(headers(user)));
  }

  /** Offers the course through the endpoint and returns the identifier of the skill. */
  private String offer(UUID user, CatalogItem course) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/tutor/skills")
                    .headers(headers(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"catalogItemId\":\"" + course.getId() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return read(body, "$.id", String.class);
  }

  private String skillsOf(UUID user) throws Exception {
    return mockMvc
        .perform(get("/api/v1/tutor/skills").headers(headers(user)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private List<SkillWithdrawn> withdrawnEventsOf(CatalogItem course) {
    return applicationEvents.stream(SkillWithdrawn.class)
        .filter(event -> event.catalogItemId().equals(course.getId()))
        .toList();
  }

  private static <T> T read(String json, String path, Class<T> type) {
    return type.cast(JsonPath.read(json, path));
  }

  /** The academic system reports the course approved with the given grade. */
  private void approved(UUID user, CatalogItem course, String grade) {
    List<ApprovedCourseView> previous = identity.approvedCourses(user);
    List<ApprovedCourseView> all = new ArrayList<>(previous);
    all.add(new ApprovedCourseView(course.getCourseCode(), course.getName(), new BigDecimal(grade), "2026-1"));
    when(identity.approvedCourses(user)).thenReturn(all);
  }

  private static String uniqueSuffix() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private CatalogItem seedCourse(String name) {
    UUID category =
        categories.save(new Category(UUID.randomUUID(), "Category " + uniqueSuffix(), (short) 0)).getId();
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(),
            CatalogScope.UNIVERSITY,
            UPC,
            category,
            name,
            null,
            "C-" + uniqueSuffix(),
            Instant.now()));
  }
}
