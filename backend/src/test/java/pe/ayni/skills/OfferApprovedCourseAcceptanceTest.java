package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;

/**
 * US13, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US13-offer-approved-course.feature},
 * with the same names, so that the acceptance criteria and what actually runs cannot drift apart
 * quietly.
 *
 * <p>{@code identity} is mocked here on purpose: identity has no implementation yet, so this is the
 * seam where skills is stubbed against the contract it depends on ({@link
 * pe.ayni.identity.IdentityApi}), exactly the way the real one will answer once it exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OfferApprovedCourseAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final BigDecimal THRESHOLD = new BigDecimal("13.00");

  /** A tutor of their own, so that one scenario cannot see another one's offers. */
  private final UUID tutor = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private CategoryRepository categories;
  @MockitoBean private IdentityApi identity;

  /** A short suffix, unique per test run, that keeps course codes within their 32-character column. */
  private static String uniqueSuffix() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private UUID seedCategory() {
    return categories.save(new Category(UUID.randomUUID(), "Category " + uniqueSuffix(), (short) 0)).getId();
  }

  private CatalogItem seedCourse(String courseCode) {
    CatalogItem item =
        new CatalogItem(
            UUID.randomUUID(),
            CatalogScope.UNIVERSITY,
            UPC,
            seedCategory(),
            "Course " + courseCode,
            null,
            courseCode,
            Instant.now());
    return catalogItems.save(item);
  }

  private CatalogItem seedGlobalTool() {
    CatalogItem item =
        new CatalogItem(
            UUID.randomUUID(),
            CatalogScope.GLOBAL,
            null,
            seedCategory(),
            "Tool " + uniqueSuffix(),
            null,
            null,
            Instant.now());
    return catalogItems.save(item);
  }

  @Test
  @DisplayName("Automatic enablement by academic record")
  void automaticEnablementByAcademicRecord() throws Exception {

    CatalogItem course = seedCourse("1ASI0657-" + uniqueSuffix());
    when(identity.approvedCourses(tutor))
        .thenReturn(
            List.of(
                new ApprovedCourseView(
                    course.getCourseCode(), course.getName(), new BigDecimal("15.50"), "2026-1")));
    when(identity.requireTenant(UPC)).thenReturn(tenant());

    mockMvc
        .perform(
            post("/api/v1/skills/offers")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", tutor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(offerBody(course.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ENABLED"))
        .andExpect(jsonPath("$.accreditationPath").value("ACADEMIC_RECORD"))
        .andExpect(jsonPath("$.accreditedGrade").value(15.50));
  }

  @Test
  @DisplayName("Suggestion from the approved courses")
  void suggestionFromTheApprovedCourses() throws Exception {

    CatalogItem course = seedCourse("1ASI0616-" + uniqueSuffix());
    when(identity.approvedCourses(tutor))
        .thenReturn(
            List.of(
                new ApprovedCourseView(
                    course.getCourseCode(), course.getName(), new BigDecimal("16.00"), "2026-1")));

    String body =
        mockMvc
            .perform(
                get("/api/v1/skills/offers/suggestions")
                    .header("X-Tenant-Id", UPC)
                    .header("X-User-Id", tutor))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> suggestedCourseCodes = JsonPath.read(body, "$[*].item.courseCode");
    assertThat(suggestedCourseCodes).contains(course.getCourseCode());
  }

  @Test
  @DisplayName("Grade below the threshold")
  void gradeBelowTheThreshold() throws Exception {

    CatalogItem course = seedCourse("1MAT0101-" + uniqueSuffix());
    when(identity.approvedCourses(tutor))
        .thenReturn(
            List.of(
                new ApprovedCourseView(
                    course.getCourseCode(), course.getName(), new BigDecimal("11.00"), "2026-1")));
    when(identity.requireTenant(UPC)).thenReturn(tenant());

    mockMvc
        .perform(
            post("/api/v1/skills/offers")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", tutor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(offerBody(course.getId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("threshold")));
  }

  @Test
  @DisplayName("Course not approved")
  void courseNotApproved() throws Exception {

    CatalogItem course = seedCourse("1ASI0625-" + uniqueSuffix());
    when(identity.approvedCourses(tutor)).thenReturn(List.of());

    String body =
        mockMvc
            .perform(
                get("/api/v1/skills/offers/suggestions")
                    .header("X-Tenant-Id", UPC)
                    .header("X-User-Id", tutor))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> suggestedCourseCodes = JsonPath.read(body, "$[*].item.courseCode");
    assertThat(suggestedCourseCodes).doesNotContain(course.getCourseCode());
  }

  @Test
  @DisplayName("Skills that are not courses")
  void skillsThatAreNotCourses() throws Exception {

    CatalogItem tool = seedGlobalTool();

    mockMvc
        .perform(
            post("/api/v1/skills/offers")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", tutor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(offerBody(tool.getId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("reviewed evidence")));

    verify(identity, never()).approvedCourses(any());
  }

  private static String offerBody(UUID catalogItemId) {
    return "{\"catalogItemId\":\"" + catalogItemId + "\"}";
  }

  private static TenantView tenant() {
    return new TenantView(UPC, "UPC", "America/Lima", THRESHOLD, true);
  }
}
