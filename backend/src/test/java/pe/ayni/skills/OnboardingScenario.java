package pe.ayni.skills;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;

/**
 * The world the scenarios of US40 and US41 start from, over HTTP with real session tokens (TS03):
 * a university of its own, whose minimum teaching grade is 13.00, with three courses and a global
 * tool, and a course of another university next to it.
 *
 * <p>Nothing is mocked. Identity, its academic system and skills are the real thing: the academic
 * directory is a folder of this test, and a student's approved courses are written into it before
 * the student imports them, the way a university's file arrives (ADR 0006). The catalogue is seeded
 * as fixtures, since loading it is a story of its own (US51).
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@Import(OnboardingTestConfig.class)
abstract class OnboardingScenario {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Path DIRECTORY = emptyFolder();

  @DynamicPropertySource
  static void anAcademicDirectoryOfItsOwn(DynamicPropertyRegistry registry) {
    registry.add("ayni.identity.academic-directory", DIRECTORY::toString);
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private CategoryRepository categories;

  protected SignedInPeople people;
  protected String tenant;

  /** Approved with 17.00: the record backs teaching it. */
  protected CatalogItem databases;

  /** Approved with 11.00, below the university's 13.00: it cannot be taught. */
  protected CatalogItem calculus;

  /** Not approved by anybody: somebody needs help with it. */
  protected CatalogItem physics;

  /** A global tool: no record backs it, only reviewed evidence. */
  protected CatalogItem python;

  /** A course of another university, which this one cannot see. */
  protected CatalogItem foreignCourse;

  /** The category of every course above, so a catalogue read can be scoped to this scenario's own. */
  protected UUID category;

  /** The approved courses of each student, by email, as the university's directory file says. */
  private final Map<String, List<String>> records = new LinkedHashMap<>();

  /** The courses of the university's curriculum, by code: every one somebody approved. */
  private final Map<String, String> curriculum = new LinkedHashMap<>();

  private static final Pattern COURSE =
      Pattern.compile("\"courseCode\": \"([^\"]+)\", \"courseName\": \"([^\"]+)\"");

  @BeforeEach
  void aUniversityWithItsCatalogue() {
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("O");
    category = categories.save(new Category(UUID.randomUUID(), "Category " + suffix(), (short) 0)).getId();
    databases = catalogItems.save(course(tenant, category, "Databases I", "DB"));
    calculus = catalogItems.save(course(tenant, category, "Calculus II", "MA"));
    physics = catalogItems.save(course(tenant, category, "Physics I", "FI"));
    python =
        catalogItems.save(
            new CatalogItem(
                UUID.randomUUID(),
                CatalogScope.GLOBAL,
                null,
                category,
                "Python " + suffix(),
                null,
                null,
                Instant.now()));
    foreignCourse = catalogItems.save(course(people.aUniversity("F"), category, "Databases I", "DB"));
  }

  /** A new student of the university, signed in, with no step of the onboarding done yet. */
  protected Person aNewStudent() {
    Person student = people.aStudent(tenant);
    approved(student, courseRecord(databases, "17.00"), courseRecord(calculus, "11.00"));
    return student;
  }

  /** What the university's academic system reports the student approved, from now on. */
  protected void approved(Person student, String... courses) {
    records.put(emailOf(student), List.of(courses));
    // Every course approved is in the curriculum of the student's career, as the academic
    // system's document requires (ADR 0006): the name is the curriculum's.
    for (String course : courses) {
      Matcher named = COURSE.matcher(course);
      if (named.find()) {
        curriculum.put(named.group(1), named.group(2));
      }
    }
    String students =
        records.entrySet().stream()
            .map(
                entry ->
                    """
                    {"email": "%s", "studentCode": "S-%s", "fullName": "From the directory",
                     "career": "Software Engineering", "cycle": 2, "currentTerm": "2026-2",
                     "status": "ACTIVE", "approvedCourses": [%s]}
                    """
                        .formatted(
                            entry.getKey(),
                            entry.getKey().substring(0, 8),
                            String.join(",", entry.getValue())))
            .collect(Collectors.joining(","));
    String courseList =
        curriculum.entrySet().stream()
            .map(
                entry ->
                    """
                    {"code": "%s", "name": "%s",
                     "careers": [{"career": "Software Engineering", "cycle": 1}]}
                    """
                        .formatted(entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(","));
    String document =
        """
        {"university": {"code": "%s", "name": "University %s", "emailDomains": ["%s.edu.pe"],
                        "timezone": "America/Lima", "minimumTeachingGrade": 13.00,
                        "currentTerm": "2026-2"},
         "careers": ["Software Engineering"],
         "courses": [%s],
         "students": [%s]}
        """
            .formatted(tenant, tenant, tenant.toLowerCase(), courseList, students);
    try {
      Files.writeString(DIRECTORY.resolve(tenant + ".json"), document);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  /**
   * One approved course of the record. The name travels with it only so {@link #approved} can put
   * the course in the curriculum; the student's record names the course by its code.
   */
  protected static String courseRecord(CatalogItem course, String grade) {
    return """
        {"courseCode": "%s", "courseName": "%s", "grade": %s, "term": "2026-1"}
        """
        .formatted(course.getCourseCode(), course.getName(), grade);
  }

  /** The profile step, as the student takes it. */
  protected void completeProfile(Person student) throws Exception {
    mockMvc
        .perform(
            as(student, put("/api/v1/me/profile"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"photoUrl\": null, \"bio\": \"I like databases\"}"))
        .andExpect(status().isOk());
  }

  /** The academic record step, as the student takes it. */
  protected void importRecord(Person student) throws Exception {
    mockMvc
        .perform(as(student, post("/api/v1/me/academic-record/import")))
        .andExpect(status().isNoContent());
  }

  /** The last step, with what the student needs and can teach. */
  protected ResultActions declare(Person student, List<UUID> learning, List<UUID> teaching)
      throws Exception {
    return mockMvc.perform(
        as(student, post("/api/v1/me/onboarding/interests"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"learning\": %s, \"teaching\": %s}".formatted(json(learning), json(teaching))));
  }

  /** Every step of the onboarding, in order. */
  protected void onboard(Person student, List<UUID> learning, List<UUID> teaching)
      throws Exception {
    completeProfile(student);
    importRecord(student);
    declare(student, learning, teaching).andExpect(status().isOk());
  }

  protected ResultActions replaceNeeds(Person student, List<UUID> learning) throws Exception {
    return mockMvc.perform(
        as(student, put("/api/v1/me/learning-interests"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"catalogItemIds\": %s}".formatted(json(learning))));
  }

  /** The body of a GET that must answer 200. */
  protected String read(Person student, String path) throws Exception {
    return mockMvc
        .perform(as(student, get(path)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** The step GET /api/v1/me shows, {@code null} when none was taken. */
  protected String stepOf(Person student) throws Exception {
    return JsonPath.read(read(student, "/api/v1/me"), "$.onboardingStep");
  }

  /** The items the student needs help with, as stored. */
  protected List<UUID> storedNeedsOf(Person student) {
    return jdbc.queryForList(
        "select catalog_item_id from skills.learning_interests where tenant_id = ? and student_id = ?",
        UUID.class,
        tenant,
        student.id());
  }

  protected MockHttpServletRequestBuilder as(Person person, MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer " + person.token());
  }

  /** The same person signing in again, with a new session. */
  protected Person signedInAgain(Person student) {
    return new Person(student.id(), student.tenantId(), people.tokenOf(tenant, student.id()));
  }

  private String emailOf(Person student) {
    return student.id() + "@" + tenant.toLowerCase() + ".edu.pe";
  }

  private static CatalogItem course(String tenant, UUID category, String name, String prefix) {
    String code = prefix + "-" + suffix();
    return new CatalogItem(
        UUID.randomUUID(),
        CatalogScope.UNIVERSITY,
        tenant,
        category,
        name + " " + code,
        null,
        code,
        Instant.now());
  }

  private static String json(List<UUID> ids) {
    List<String> quoted = new ArrayList<>();
    ids.forEach(id -> quoted.add("\"" + id + "\""));
    return "[" + String.join(",", quoted) + "]";
  }

  private static String suffix() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private static Path emptyFolder() {
    try {
      Path folder = Files.createTempDirectory("ayni-onboarding-directory");
      folder.toFile().deleteOnExit();
      return folder;
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
