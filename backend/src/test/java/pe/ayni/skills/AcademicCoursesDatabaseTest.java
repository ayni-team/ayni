package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.skills.application.LoadAcademicCatalogUseCase;
import pe.ayni.skills.infrastructure.CatalogItemRepository;

/**
 * Loading the curriculum over HTTP against a real PostgreSQL: that the courses are saved for the
 * university that loaded them and found by its students, that a bad list saves nothing, and that two
 * coordinators loading the same curriculum at once do not collide.
 *
 * <p>Every test works in a university of its own and with codes of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AcademicCoursesDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private final UUID coordinator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;

  private String tenant;
  private String suffix;

  @BeforeEach
  void aUniversityWithACoordinator() {
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
                  "someone@u.pe",
                  moderator ? null : "U1",
                  "Someone",
                  null,
                  null,
                  null);
            });
  }

  private ResultActions load(String tenantId, UUID user, String json) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/academic-catalog/courses")
            .header("X-Tenant-Id", tenantId)
            .header("X-User-Id", user.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions load(String json) throws Exception {
    return load(tenant, coordinator, json);
  }

  private String courses() throws Exception {
    return body(
        mockMvc
            .perform(
                get("/api/v1/coordinator/academic-catalog/courses")
                    .header("X-Tenant-Id", tenant)
                    .header("X-User-Id", coordinator.toString()))
            .andExpect(status().isOk()));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private String course(String code, String name) {
    return "{\"code\":\"" + code + suffix + "\",\"name\":\"" + name + "\"}";
  }

  @Test
  @DisplayName("loaded courses are saved for the university, listed and found by its students")
  void loadedCoursesAreSavedListedAndFound() throws Exception {
    String answer =
        body(
            load("{\"courses\":[" + course("ARQ", "Software Architecture") + "," + course("BD", "Databases") + "]}")
                .andExpect(status().isOk()));

    assertThat(JsonPath.<Integer>read(answer, "$.created")).isEqualTo(2);
    assertThat(JsonPath.<List<String>>read(answer, "$.courses[*].outcome")).containsExactly("CREATED", "CREATED");
    String listed = courses();
    assertThat(JsonPath.<List<String>>read(listed, "$[*].code")).containsExactly("ARQ" + suffix, "BD" + suffix);
    assertThat(JsonPath.<List<String>>read(listed, "$[*].status")).containsExactly("ACTIVE", "ACTIVE");
    String found =
        body(
            mockMvc
                .perform(get("/api/v1/catalog").header("X-Tenant-Id", tenant).param("q", "Databases"))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(found, "$.items[?(@.name == 'Databases')].name")).containsExactly("Databases");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from skills.categories where name = ?",
                Integer.class,
                LoadAcademicCatalogUseCase.COURSES_CATEGORY))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("another university does not see the courses")
  void anotherUniversityDoesNotSeeThem() throws Exception {
    String name = "Cryptography " + suffix;
    load("{\"courses\":[" + course("CRY", name) + "]}").andExpect(status().isOk());

    String other = "O" + suffix;
    assertThat(catalogItems.findByTenantIdAndCourseCodeIn(other, List.of("CRY" + suffix))).isEmpty();
    String mine =
        body(mockMvc.perform(get("/api/v1/catalog").header("X-Tenant-Id", tenant).param("q", name)).andExpect(status().isOk()));
    String theirs =
        body(mockMvc.perform(get("/api/v1/catalog").header("X-Tenant-Id", other).param("q", name)).andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(mine, "$.items[*].name")).containsExactly(name);
    assertThat(JsonPath.<List<String>>read(theirs, "$.items[*].name")).isEmpty();
  }

  @Test
  @DisplayName("loading the same list again changes nothing, and a new name is an update")
  void loadingAgain() throws Exception {
    String list = "{\"courses\":[" + course("ARQ", "Software Architecture") + "]}";
    load(list).andExpect(status().isOk());

    String again = body(load(list).andExpect(status().isOk()));
    assertThat(JsonPath.<Integer>read(again, "$.unchanged")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(again, "$.created")).isZero();

    String renamed =
        body(load("{\"courses\":[" + course("ARQ", "Architecture of Software") + "]}").andExpect(status().isOk()));
    assertThat(JsonPath.<Integer>read(renamed, "$.updated")).isEqualTo(1);
    assertThat(JsonPath.<List<String>>read(courses(), "$[*].name")).containsExactly("Architecture of Software");
  }

  @Test
  @DisplayName("a bad list saves nothing: not even the valid courses before the bad one")
  void aBadListSavesNothing() throws Exception {
    load("{\"courses\":[" + course("ARQ", "Software Architecture") + ",{\"code\":\"X" + suffix + "\",\"name\":\" \"}]}")
        .andExpect(status().isBadRequest());
    load("{\"courses\":[" + course("ARQ", "Software Architecture") + "," + course("ARQ", "Again") + "]}")
        .andExpect(status().isBadRequest());
    load("{\"courses\":[]}").andExpect(status().isBadRequest());
    load("{}").andExpect(status().isBadRequest());
    load("not json").andExpect(status().isBadRequest());

    assertThat(JsonPath.<List<String>>read(courses(), "$[*].code")).isEmpty();
  }

  @Test
  @DisplayName("a student cannot load or list the courses")
  void aStudentCannotLoadOrList() throws Exception {
    load(tenant, student, "{\"courses\":[" + course("ARQ", "Software Architecture") + "]}")
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/coordinator/academic-catalog/courses")
                .header("X-Tenant-Id", tenant)
                .header("X-User-Id", student.toString()))
        .andExpect(status().isForbidden());

    assertThat(JsonPath.<List<String>>read(courses(), "$[*].code")).isEmpty();
  }

  @Test
  @DisplayName("two coordinators loading the same curriculum at once end with each course once")
  void twoLoadingTheSameCurriculumAtOnce() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 5; round++) {
        String roundSuffix = suffix + round;
        String list =
            "{\"courses\":[{\"code\":\"A" + roundSuffix + "\",\"name\":\"One\"},{\"code\":\"B" + roundSuffix
                + "\",\"name\":\"Two\"},{\"code\":\"C" + roundSuffix + "\",\"name\":\"Three\"}]}";
        CyclicBarrier together = new CyclicBarrier(2);
        Future<Integer> one = pool.submit(() -> loadTogether(together, list));
        Future<Integer> two = pool.submit(() -> loadTogether(together, list));

        assertThat(one.get()).isEqualTo(200);
        assertThat(two.get()).isEqualTo(200);
        assertThat(
                catalogItems.findByTenantIdAndCourseCodeIn(
                    tenant, List.of("A" + roundSuffix, "B" + roundSuffix, "C" + roundSuffix)))
            .hasSize(3);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private int loadTogether(CyclicBarrier together, String list) throws Exception {
    together.await();
    return load(list).andReturn().getResponse().getStatus();
  }
}
