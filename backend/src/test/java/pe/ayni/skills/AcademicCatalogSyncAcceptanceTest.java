package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.AcademicDocuments;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * Synchronising the curriculum from the academic system, and two universities kept apart (ADR 0006,
 * amended 2026-10-23), over HTTP against a real PostgreSQL with real session tokens (TS03). One test
 * per scenario of {@code features/academic-system-documents.feature} that names this class, with
 * the same display name.
 *
 * <p>Nothing is stubbed: identity reads an academic directory of this test, and skills asks it for
 * the curriculum through {@code IdentityApi.academicCurriculum}. The scenarios of the
 * synchronisation use the small documents of {@link AcademicDocuments}; the one about two
 * universities uses the documents the repository delivers, under codes and domains of their own so
 * they do not meet the universities other tests registered.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class AcademicCatalogSyncAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Path DIRECTORY = emptyFolder();

  /** The folder the backend reads when it runs from {@code backend/}. */
  private static final Path DELIVERED = Path.of("academic-directory");

  private static final ObjectMapper JSON = new ObjectMapper();

  @DynamicPropertySource
  static void anAcademicDirectoryOfItsOwn(DynamicPropertyRegistry registry) {
    registry.add("ayni.identity.academic-directory", DIRECTORY::toString);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformAdminRepository administrators;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  private SignedInPeople people;

  @BeforeEach
  void people() {
    people = new SignedInPeople(tenants, users, userSessions, tokens);
  }

  @Test
  @DisplayName("A coordinator synchronises the curriculum of the university")
  void aCoordinatorSynchronisesTheCurriculum() throws Exception {
    // A university registered with 13.00 as its minimum grade, whose document says 14.5.
    String university = people.aUniversity("S");
    document(university);
    Person coordinator = people.aCoordinator(university);

    sync(coordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(3))
        .andExpect(jsonPath("$.updated").value(0))
        .andExpect(jsonPath("$.reinstated").value(0))
        .andExpect(jsonPath("$.unchanged").value(0));

    assertThat(coursesOf(university))
        .containsExactly(
            "DER101 Derecho Civil I ACTIVE", "ECO101 Microeconomía ACTIVE", "GEN100 Lenguaje ACTIVE");
    // The grade is the registration's and the coordinator's, never the document's.
    String grade = read(coordinator, "/api/v1/coordinator/academic-catalog/minimum-grade");
    assertThat(JsonPath.<Double>read(grade, "$.minimumGrade")).isEqualTo(13.0);
  }

  @Test
  @DisplayName("Synchronising twice in a row does not duplicate a course")
  void synchronisingTwiceDoesNotDuplicate() throws Exception {
    String university = people.aUniversity("S");
    document(university);
    Person coordinator = people.aCoordinator(university);
    sync(coordinator).andExpect(status().isOk());

    sync(coordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.unchanged").value(3));

    assertThat(coursesOf(university)).hasSize(3);
  }

  @Test
  @DisplayName("Synchronising never removes a course")
  void synchronisingNeverRemovesACourse() throws Exception {
    String university = people.aUniversity("S");
    document(university);
    Person coordinator = people.aCoordinator(university);
    sync(coordinator).andExpect(status().isOk());

    // By hand: a course the academic system does not have, a description, and a course retired.
    mockMvc
        .perform(
            as(coordinator, post("/api/v1/coordinator/academic-catalog/courses"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"courses": [
                      {"code": "TALLER1", "name": "Taller de Oratoria"},
                      {"code": "GEN100", "name": "Lenguaje", "description": "Redacción y lectura"}]}
                    """))
        .andExpect(status().isOk());
    UUID der101 =
        jdbc.queryForObject(
            "select id from skills.catalog_items where tenant_id = ? and course_code = 'DER101'",
            UUID.class,
            university);
    mockMvc
        .perform(
            as(coordinator, post("/api/v1/coordinator/academic-catalog/courses/" + der101 + "/retire"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedTutors\": 0}"))
        .andExpect(status().isOk());

    sync(coordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.reinstated").value(1));

    // Nothing is deleted: the course of the coordinator stays, the retired one is back as loading
    // the curriculum brings it back (US51), and the description given by hand is kept.
    assertThat(coursesOf(university))
        .containsExactly(
            "DER101 Derecho Civil I ACTIVE",
            "ECO101 Microeconomía ACTIVE",
            "GEN100 Lenguaje ACTIVE",
            "TALLER1 Taller de Oratoria ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "select description from skills.catalog_items where tenant_id = ? and course_code = 'GEN100'",
                String.class,
                university))
        .isEqualTo("Redacción y lectura");
  }

  @Test
  @DisplayName("A student cannot synchronise the curriculum")
  void aStudentCannotSynchronise() throws Exception {
    String university = people.aUniversity("S");
    document(university);

    sync(people.aStudent(university)).andExpect(status().isForbidden());

    assertThat(coursesOf(university)).isEmpty();
  }

  @Test
  @DisplayName("Each university sees only its own curriculum and its own brand")
  void eachUniversitySeesOnlyItsOwn() throws Exception {
    String administrator = anAdministratorSignedIn();
    String upc = delivered("UPC");
    String usil = delivered("USIL");
    for (String code : List.of(upc, usil)) {
      mockMvc
          .perform(
              post("/api/v1/admin/universities/from-academic-system")
                  .header("Authorization", "Bearer " + administrator)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"code\": \"%s\", \"initialCredits\": 5, \"creditValidityDays\": 30}"
                          .formatted(code)))
          .andExpect(status().isCreated());
      sync(people.aCoordinator(code)).andExpect(status().isOk());
    }

    Person ofUpc = people.aStudent(upc);
    Person ofUsil = people.aStudent(usil);
    List<String> upcCourses = universityCoursesSeenBy(ofUpc);
    List<String> usilCourses = universityCoursesSeenBy(ofUsil);

    assertThat(upcCourses).hasSize(courseCountOf("UPC")).contains("SI400 Diseño de Base de Datos");
    assertThat(usilCourses).hasSize(courseCountOf("USIL")).contains("USIL001 Fundamentos de Programación");
    assertThat(upcCourses).noneMatch(course -> course.startsWith("USIL"));
    assertThat(usilCourses).noneMatch(course -> course.startsWith("SI400"));

    String upcBrand = read(ofUpc, "/api/v1/university");
    assertThat(JsonPath.<String>read(upcBrand, "$.code")).isEqualTo(upc);
    assertThat(JsonPath.<String>read(upcBrand, "$.name")).isEqualTo("Universidad Peruana de Ciencias Aplicadas");
    assertThat(JsonPath.<String>read(upcBrand, "$.logoUrl")).isEqualTo("/logos/upc.png");
    assertThat(JsonPath.<String>read(upcBrand, "$.primaryColor")).isEqualTo("#E42322");
    String usilBrand = read(ofUsil, "/api/v1/university");
    assertThat(JsonPath.<String>read(usilBrand, "$.code")).isEqualTo(usil);
    assertThat(JsonPath.<String>read(usilBrand, "$.name")).isEqualTo("Universidad San Ignacio de Loyola");
    assertThat(JsonPath.<String>read(usilBrand, "$.logoUrl")).isEqualTo("/logos/usil.jpg");
    assertThat(JsonPath.<String>read(usilBrand, "$.primaryColor")).isEqualTo("#07256D");
  }

  /** The university courses a student finds in {@code GET /api/v1/catalog}, as code and name. */
  private List<String> universityCoursesSeenBy(Person student) throws Exception {
    List<String> seen = new ArrayList<>();
    int pages = 1;
    for (int page = 0; page < pages; page++) {
      String found = read(student, "/api/v1/catalog?size=100&page=" + page);
      List<String> codes = JsonPath.read(found, "$.items[?(@.scope == 'UNIVERSITY')].courseCode");
      List<String> names = JsonPath.read(found, "$.items[?(@.scope == 'UNIVERSITY')].name");
      IntStream.range(0, codes.size()).forEach(i -> seen.add(codes.get(i) + " " + names.get(i)));
      pages = JsonPath.read(found, "$.totalPages");
    }
    return seen;
  }

  private ResultActions sync(Person person) throws Exception {
    return mockMvc.perform(as(person, post("/api/v1/coordinator/academic-catalog/sync")));
  }

  private String read(Person person, String path) throws Exception {
    return mockMvc
        .perform(as(person, get(path)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static MockHttpServletRequestBuilder as(Person person, MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer " + person.token());
  }

  private List<String> coursesOf(String university) {
    return jdbc.queryForList(
        "select course_code || ' ' || name || ' ' || status from skills.catalog_items"
            + " where tenant_id = ? order by course_code",
        String.class,
        university);
  }

  private String anAdministratorSignedIn() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    administrators.save(
        new PlatformAdmin(id, "admin-" + id + "@ayni.local", "Administrator", PlatformAdminStatus.ACTIVE, now));
    String token = tokens.generate().rawToken();
    userSessions.save(
        UserSession.forPlatformAdmin(
            UUID.randomUUID(), id, tokens.hash(token), now.plus(Duration.ofDays(1)), now));
    return token;
  }

  /** The small document of {@link AcademicDocuments} for a university, with one student. */
  private static void document(String university) {
    write(
        university,
        AcademicDocuments.document(
            university,
            AcademicDocuments.student("nora@" + university.toLowerCase(Locale.ROOT) + ".edu.pe", "N1")));
  }

  /**
   * A delivered document under a code and a domain nobody else uses: the curriculum, the name, the
   * logo and the colours stay as delivered.
   */
  private static String delivered(String code) throws IOException {
    String fresh = code + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    String domain = fresh.toLowerCase(Locale.ROOT) + ".edu.pe";
    ObjectNode document = (ObjectNode) JSON.readTree(DELIVERED.resolve(code + ".json").toFile());
    ObjectNode university = (ObjectNode) document.get("university");
    String original = university.get("emailDomains").get(0).asText();
    university.put("code", fresh);
    university.putArray("emailDomains").add(domain);
    for (JsonNode student : (ArrayNode) document.get("students")) {
      ((ObjectNode) student).put("email", student.get("email").asText().replace("@" + original, "@" + domain));
    }
    write(fresh, JSON.writeValueAsString(document));
    return fresh;
  }

  private static int courseCountOf(String code) throws IOException {
    return JSON.readTree(DELIVERED.resolve(code + ".json").toFile()).get("courses").size();
  }

  private static void write(String university, String document) {
    try {
      Files.writeString(DIRECTORY.resolve(university + ".json"), document);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static Path emptyFolder() {
    try {
      Path folder = Files.createTempDirectory("ayni-academic-sync");
      folder.toFile().deleteOnExit();
      return folder;
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
