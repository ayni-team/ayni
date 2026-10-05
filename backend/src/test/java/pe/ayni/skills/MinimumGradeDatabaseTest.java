package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.skills.infrastructure.AcademicSettingsRepository;

/**
 * The minimum grade over HTTP against a real PostgreSQL: that it is kept per university, that bad
 * values are refused before anything is saved, and that two coordinators saving the first value at
 * once do not collide on the key.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MinimumGradeDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private final UUID coordinator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private AcademicSettingsRepository settings;
  @MockitoBean private IdentityApi identity;

  private String tenant;

  @BeforeEach
  void aUniversityWithACoordinator() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
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
    when(identity.requireTenant(tenant))
        .thenReturn(new TenantView(tenant, "University", "America/Lima", new BigDecimal("13.00"), true));
  }

  private ResultActions set(UUID user, String json) throws Exception {
    return mockMvc.perform(
        put("/api/v1/coordinator/academic-catalog/minimum-grade")
            .header("X-Tenant-Id", tenant)
            .header("X-User-Id", user.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions read(UUID user) throws Exception {
    return mockMvc.perform(
        get("/api/v1/coordinator/academic-catalog/minimum-grade")
            .header("X-Tenant-Id", tenant)
            .header("X-User-Id", user.toString()));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("before a coordinator sets it, the grade is the registered one and has no date")
  void beforeItIsSet() throws Exception {
    String answer = body(read(coordinator).andExpect(status().isOk()));

    assertThat(JsonPath.<Double>read(answer, "$.minimumGrade")).isEqualTo(13.0);
    assertThat(JsonPath.<Object>read(answer, "$.updatedAt")).isNull();
  }

  @Test
  @DisplayName("setting it twice keeps one row for the university with the latest value")
  void settingItTwiceKeepsTheLatest() throws Exception {
    set(coordinator, "{\"minimumGrade\":15}").andExpect(status().isOk());
    set(coordinator, "{\"minimumGrade\":11.5}").andExpect(status().isOk());

    String answer = body(read(coordinator).andExpect(status().isOk()));
    assertThat(JsonPath.<Double>read(answer, "$.minimumGrade")).isEqualTo(11.5);
    assertThat(JsonPath.<String>read(answer, "$.updatedAt")).isNotBlank();
    assertThat(settings.findById(tenant).orElseThrow().getUpdatedBy()).isEqualTo(coordinator);
    assertThat(settings.findAll().stream().filter(row -> row.getTenantId().equals(tenant))).hasSize(1);
  }

  @Test
  @DisplayName("setting it for one university leaves another one alone")
  void eachUniversityKeepsItsOwn() throws Exception {
    set(coordinator, "{\"minimumGrade\":17}").andExpect(status().isOk());

    String other = "T" + UUID.randomUUID().toString().substring(0, 8);

    assertThat(settings.findById(other)).isEmpty();
    assertThat(settings.findById(tenant)).isPresent();
  }

  @Test
  @DisplayName("a grade off the scale, missing or not a number is a 400 and a student is a 403")
  void invalidRequests() throws Exception {
    set(coordinator, "{\"minimumGrade\":21}").andExpect(status().isBadRequest());
    set(coordinator, "{\"minimumGrade\":-1}").andExpect(status().isBadRequest());
    set(coordinator, "{\"minimumGrade\":13.456}").andExpect(status().isBadRequest());
    set(coordinator, "{}").andExpect(status().isBadRequest());
    set(coordinator, "{\"minimumGrade\":\"high\"}").andExpect(status().isBadRequest());
    set(student, "{\"minimumGrade\":10}").andExpect(status().isForbidden());
    read(student).andExpect(status().isForbidden());

    assertThat(settings.findById(tenant)).isEmpty();
  }

  @Test
  @DisplayName("two coordinators saving the first value at once both succeed")
  void twoSavingTheFirstValueAtOnce() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 5; round++) {
        String roundTenant = "T" + UUID.randomUUID().toString().substring(0, 8);
        CyclicBarrier together = new CyclicBarrier(2);
        Future<?> one = pool.submit(() -> save(together, roundTenant, new BigDecimal("12.00")));
        Future<?> two = pool.submit(() -> save(together, roundTenant, new BigDecimal("16.00")));
        one.get();
        two.get();

        BigDecimal kept = settings.findById(roundTenant).orElseThrow().getMinimumTeachingGrade();
        assertThat(kept).isIn(new BigDecimal("12.00"), new BigDecimal("16.00"));
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private void save(CyclicBarrier together, String tenantId, BigDecimal grade) {
    try {
      together.await();
      settings.saveMinimumGrade(tenantId, grade, UUID.randomUUID(), Instant.now());
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }
}
