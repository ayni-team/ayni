package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;

/**
 * US51, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US51-configure-the-academic-catalogue.feature},
 * with the same names. The identity of each person and the courses and grades the academic system
 * reports for them are stubbed, since identity is another module; everything else is the real
 * application.
 *
 * <p>Each test works in a university of its own and with course codes and names of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AcademicCatalogAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private final UUID coordinator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;

  private String tenant;
  private String suffix;
  private final Map<UUID, List<ApprovedCourseView>> records = new ConcurrentHashMap<>();

  /** Background: a coordinator, students, and a university that registered with 13. */
  @BeforeEach
  void aCoordinatorStudentsAndAGradeOfThirteen() {
    suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    tenant = "T" + suffix;
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              boolean moderator = coordinator.equals(id);
              return new UserView(
                  id,
                  tenant,
                  moderator ? UserRole.COORDINATOR : UserRole.STUDENT,
                  "someone@upc.edu.pe",
                  moderator ? null : "U202310949",
                  moderator ? "Carla Rios" : "Ana Torres",
                  null,
                  null,
                  null);
            });
    when(identity.requireTenant(tenant))
        .thenReturn(new TenantView(tenant, "University", "America/Lima", new BigDecimal("13.00"), true));
    when(identity.approvedCourses(any(UUID.class)))
        .thenAnswer(call -> records.getOrDefault(call.<UUID>getArgument(0), List.of()));
  }

  private UUID studentWith(String grade, String courseCode, String courseName) {
    UUID id = UUID.randomUUID();
    records.put(id, List.of(new ApprovedCourseView(courseCode, courseName, new BigDecimal(grade), "2026-1")));
    return id;
  }

  private HttpHeaders headers(UUID user) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", tenant);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private String code(String prefix) {
    return prefix + suffix;
  }

  private static String listOf(String... courses) {
    return "{\"courses\":[" + String.join(",", courses) + "]}";
  }

  private static String course(String code, String name) {
    return "{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}";
  }

  private ResultActions loadCourses(UUID user, String json) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/academic-catalog/courses")
            .headers(headers(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions setGrade(UUID user, String grade) throws Exception {
    return mockMvc.perform(
        put("/api/v1/coordinator/academic-catalog/minimum-grade")
            .headers(headers(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"minimumGrade\":" + grade + "}"));
  }

  private ResultActions offer(UUID user, UUID courseId) throws Exception {
    return mockMvc.perform(
        post("/api/v1/tutor/skills")
            .headers(headers(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"catalogItemId\":\"" + courseId + "\"}"));
  }

  private ResultActions retire(UUID courseId, long confirmedTutors) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/academic-catalog/courses/" + courseId + "/retire")
            .headers(headers(coordinator))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"confirmedTutors\":" + confirmedTutors + "}"));
  }

  private String skillsOf(UUID user) throws Exception {
    return body(mockMvc.perform(get("/api/v1/tutor/skills").headers(headers(user))).andExpect(status().isOk()));
  }

  private String courses() throws Exception {
    return body(
        mockMvc
            .perform(get("/api/v1/coordinator/academic-catalog/courses").headers(headers(coordinator)))
            .andExpect(status().isOk()));
  }

  private String searched(String name) throws Exception {
    return body(
        mockMvc
            .perform(get("/api/v1/catalog").header("X-Tenant-Id", tenant).param("q", name))
            .andExpect(status().isOk()));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private UUID idOf(String courseCode) throws Exception {
    List<String> ids = JsonPath.read(courses(), "$[?(@.code == '" + courseCode + "')].id");
    return UUID.fromString(ids.get(0));
  }

  private UUID bookingOn(UUID courseId, UUID tutor) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into booking.bookings
          (id, tenant_id, student_id, tutor_id, catalog_item_id, starts_at, ends_at, hours, credits_charged,
           need_description, status)
        values (?, ?, ?, ?, ?, now() + interval '2 days', now() + interval '2 days 1 hour', 1, 1, 'Help with it', 'CONFIRMED')
        """,
        id,
        tenant,
        student,
        tutor,
        courseId);
    return id;
  }

  private Tuple bookingState(UUID booking) {
    var row = jdbc.queryForMap("select status, catalog_item_id from booking.bookings where id = ?", booking);
    return Tuple.tuple(row.get("status"), row.get("catalog_item_id"));
  }

  @Test
  @DisplayName("Available courses")
  void availableCourses() throws Exception {

    String databases = "Databases " + suffix;
    String architecture = "Architecture " + suffix;
    loadCourses(
            coordinator,
            listOf(course(code("BD"), databases), course(code("ARQ"), architecture)))
        .andExpect(status().isOk());
    UUID tutor = studentWith("15.00", code("BD"), databases);

    String foundDatabases = searched(databases);
    String foundArchitecture = searched(architecture);
    ResultActions offered = offer(tutor, idOf(code("BD")));

    assertThat(JsonPath.<List<String>>read(foundDatabases, "$.items[*].name")).containsExactly(databases);
    assertThat(JsonPath.<List<String>>read(foundArchitecture, "$.items[*].name")).containsExactly(architecture);
    offered.andExpect(status().isCreated());
    assertThat(JsonPath.<List<String>>read(skillsOf(tutor), "$[*].status")).containsExactly("ENABLED");
  }

  @Test
  @DisplayName("Minimum grade to teach")
  void minimumGradeToTeach() throws Exception {

    String name = "Algorithms " + suffix;
    loadCourses(coordinator, listOf(course(code("ALG"), name))).andExpect(status().isOk());
    UUID courseId = idOf(code("ALG"));
    setGrade(coordinator, "15").andExpect(status().isOk());
    UUID below = studentWith("14.00", code("ALG"), name);
    UUID above = studentWith("16.00", code("ALG"), name);

    offer(below, courseId).andExpect(status().isBadRequest());
    offer(above, courseId).andExpect(status().isCreated());

    assertThat(JsonPath.<List<String>>read(skillsOf(below), "$[*].status")).isEmpty();
    assertThat(JsonPath.<List<String>>read(skillsOf(above), "$[*].status")).containsExactly("ENABLED");
  }

  @Test
  @DisplayName("Change of the minimum grade")
  void changeOfTheMinimumGrade() throws Exception {

    String name = "Networks " + suffix;
    loadCourses(coordinator, listOf(course(code("RED"), name))).andExpect(status().isOk());
    UUID courseId = idOf(code("RED"));
    UUID enabledBefore = studentWith("14.00", code("RED"), name);
    offer(enabledBefore, courseId).andExpect(status().isCreated());

    setGrade(coordinator, "16").andExpect(status().isOk());

    assertThat(JsonPath.<List<String>>read(skillsOf(enabledBefore), "$[*].status")).containsExactly("ENABLED");
    assertThat(JsonPath.<List<Double>>read(skillsOf(enabledBefore), "$[*].accreditedGrade")).containsExactly(14.0);
    UUID after = studentWith("14.00", code("RED"), name);
    offer(after, courseId).andExpect(status().isBadRequest());
    assertThat(JsonPath.<List<String>>read(skillsOf(after), "$[*].status")).isEmpty();
  }

  @Test
  @DisplayName("Withdrawal of a course")
  void withdrawalOfACourse() throws Exception {

    String name = "Compilers " + suffix;
    loadCourses(coordinator, listOf(course(code("CMP"), name))).andExpect(status().isOk());
    UUID courseId = idOf(code("CMP"));
    UUID tutor = studentWith("17.00", code("CMP"), name);
    offer(tutor, courseId).andExpect(status().isCreated());
    UUID booking = bookingOn(courseId, tutor);

    String usage =
        body(
            mockMvc
                .perform(get("/api/v1/coordinator/catalog/" + courseId + "/usage").headers(headers(coordinator)))
                .andExpect(status().isOk()));
    long affected = JsonPath.<Integer>read(usage, "$.tutorsOffering");
    retire(courseId, affected).andExpect(status().isOk());

    offer(studentWith("19.00", code("CMP"), name), courseId).andExpect(status().isBadRequest());
    assertThat(JsonPath.<List<String>>read(searched(name), "$.items[*].name")).isEmpty();
    assertThat(JsonPath.<List<String>>read(courses(), "$[*].status")).containsExactly("RETIRED");
    assertThat(JsonPath.<List<String>>read(skillsOf(tutor), "$[*].status")).containsExactly("WITHDRAWN");
    assertThat(bookingState(booking)).isEqualTo(Tuple.tuple("CONFIRMED", courseId));
    assertThat(affected).isEqualTo(1);
  }

  @Test
  @DisplayName("A student cannot configure the academic catalogue")
  void aStudentCannotConfigureTheAcademicCatalogue() throws Exception {

    String name = "Security " + suffix;
    loadCourses(coordinator, listOf(course(code("SEG"), name))).andExpect(status().isOk());
    UUID courseId = idOf(code("SEG"));

    loadCourses(student, listOf(course(code("NEW"), "Not loaded " + suffix))).andExpect(status().isForbidden());
    setGrade(student, "5").andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/coordinator/academic-catalog/minimum-grade").headers(headers(student)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/coordinator/academic-catalog/courses/" + courseId + "/retire")
                .headers(headers(student))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedTutors\":0}"))
        .andExpect(status().isForbidden());

    assertThat(JsonPath.<List<String>>read(courses(), "$[*].code")).containsExactly(code("SEG"));
    assertThat(JsonPath.<List<String>>read(courses(), "$[*].status")).containsExactly("ACTIVE");
    String grade =
        body(
            mockMvc
                .perform(get("/api/v1/coordinator/academic-catalog/minimum-grade").headers(headers(coordinator)))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<Double>read(grade, "$.minimumGrade")).isEqualTo(13.0);
  }

  @Test
  @DisplayName("A list that cannot be loaded saves nothing")
  void aListThatCannotBeLoadedSavesNothing() throws Exception {

    loadCourses(coordinator, listOf(course(code("OK"), "Fine " + suffix), "{\"code\":\"" + code("BAD") + "\",\"name\":\"\"}"))
        .andExpect(status().isBadRequest());

    assertThat(JsonPath.<List<String>>read(courses(), "$[*].code")).isEmpty();
  }
}
