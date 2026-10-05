package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.TaughtSessionRepository;

/**
 * The use of a catalogue item over HTTP against a real PostgreSQL: the tutors who offer it, the
 * sessions taught on it, and the event that feeds the second.
 *
 * <p>Every test works with an item of its own, and universities of their own, so what other tests
 * left does not count.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogUsageDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final UUID coordinator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private OfferedSkillRepository offeredSkills;
  @Autowired private TaughtSessionRepository taughtSessions;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @MockitoBean private IdentityApi identity;

  private String tenant;
  private UUID category;

  @BeforeEach
  void aUniversityWithACoordinator() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    category = categories.save(new Category(UUID.randomUUID(), "Category " + tenant, (short) 0)).getId();
    when(identity.requireUser(coordinator))
        .thenReturn(new UserView(coordinator, tenant, UserRole.COORDINATOR, "c@u.pe", null, "C", null, null, null));
    when(identity.requireUser(student))
        .thenReturn(new UserView(student, tenant, UserRole.STUDENT, "s@u.pe", "U1", "S", null, null, null));
  }

  private CatalogItem tool() {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, category, "Tool " + UUID.randomUUID(), null, null, NOW));
  }

  private CatalogItem course(String tenantCode) {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(),
            CatalogScope.UNIVERSITY,
            tenantCode,
            category,
            "Course " + UUID.randomUUID(),
            null,
            "C" + UUID.randomUUID().toString().substring(0, 8),
            NOW));
  }

  private void enabledBy(String tenantCode, CatalogItem item) {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), tenantCode, UUID.randomUUID(), item.getId(), NOW);
    skill.approveByReviewedEvidence(NOW);
    offeredSkills.save(skill);
  }

  private void pendingBy(String tenantCode, CatalogItem item) {
    offeredSkills.save(
        OfferedSkill.requestValidation(UUID.randomUUID(), tenantCode, UUID.randomUUID(), item.getId(), NOW));
  }

  private void taught(String tenantCode, CatalogItem item) {
    taughtSessions.recordIfNew(UUID.randomUUID(), tenantCode, item.getId(), UUID.randomUUID(), NOW);
  }

  private HttpHeaders headers(UUID user, String tenantCode) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", tenantCode);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private ResultActions usage(UUID user, String tenantCode, CatalogItem item) throws Exception {
    return mockMvc.perform(
        get("/api/v1/coordinator/catalog/" + item.getId() + "/usage").headers(headers(user, tenantCode)));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("the tutors who offer an item and the sessions taught on it are counted")
  void theTutorsAndTheSessionsAreCounted() throws Exception {
    CatalogItem figma = tool();
    enabledBy(tenant, figma);
    enabledBy(tenant, figma);
    pendingBy(tenant, figma);
    taught(tenant, figma);
    taught(tenant, figma);
    taught(tenant, figma);

    String answer = body(usage(coordinator, tenant, figma).andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(answer, "$.item.id")).isEqualTo(figma.getId().toString());
    assertThat(JsonPath.<String>read(answer, "$.item.scope")).isEqualTo("GLOBAL");
    assertThat(JsonPath.<String>read(answer, "$.item.status")).isEqualTo("ACTIVE");
    assertThat(JsonPath.<Integer>read(answer, "$.tutorsOffering")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(answer, "$.sessionsTaught")).isEqualTo(3);
  }

  @Test
  @DisplayName("a global tool counts every university, because retiring it affects all of them")
  void aGlobalToolCountsEveryUniversity() throws Exception {
    CatalogItem figma = tool();
    String other = "T" + UUID.randomUUID().toString().substring(0, 8);
    enabledBy(tenant, figma);
    enabledBy(other, figma);
    enabledBy(other, figma);
    taught(tenant, figma);
    taught(other, figma);

    String answer = body(usage(coordinator, tenant, figma).andExpect(status().isOk()));

    assertThat(JsonPath.<Integer>read(answer, "$.tutorsOffering")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(answer, "$.sessionsTaught")).isEqualTo(2);
  }

  @Test
  @DisplayName("an item nobody uses has zeros, and a retired one is still reviewed")
  void anItemNobodyUsesHasZeros() throws Exception {
    CatalogItem figma = tool();
    figma.retire();
    catalogItems.save(figma);

    String answer = body(usage(coordinator, tenant, figma).andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(answer, "$.item.status")).isEqualTo("RETIRED");
    assertThat(JsonPath.<Integer>read(answer, "$.tutorsOffering")).isZero();
    assertThat(JsonPath.<Integer>read(answer, "$.sessionsTaught")).isZero();
  }

  @Test
  @DisplayName("a course of another university is not found")
  void aCourseOfAnotherUniversityIsNotFound() throws Exception {
    String other = "T" + UUID.randomUUID().toString().substring(0, 8);
    CatalogItem theirs = course(other);

    usage(coordinator, tenant, theirs).andExpect(status().isNotFound());
    usage(coordinator, tenant, course(tenant)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("an item that does not exist is not found, and a student is refused")
  void anItemThatDoesNotExistIsNotFoundAndAStudentIsRefused() throws Exception {
    CatalogItem figma = tool();

    mockMvc
        .perform(
            get("/api/v1/coordinator/catalog/" + UUID.randomUUID() + "/usage").headers(headers(coordinator, tenant)))
        .andExpect(status().isNotFound());
    usage(student, tenant, figma).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("a completed session is counted once, even when its announcement arrives twice")
  void aCompletedSessionIsCountedOnce() throws Exception {
    CatalogItem figma = tool();
    UUID session = UUID.randomUUID();
    SessionCompleted completed =
        new SessionCompleted(
            tenant, session, UUID.randomUUID(), UUID.randomUUID(), student, figma.getId(), Credits.of(1), NOW);

    transactions.executeWithoutResult(status -> events.publishEvent(completed));
    transactions.executeWithoutResult(status -> events.publishEvent(completed));

    await().atMost(Duration.ofSeconds(10)).until(() -> taughtSessions.countByCatalogItemId(figma.getId()) >= 1);
    // Give the second delivery the time it needs to do harm, if it could.
    Thread.sleep(500);
    assertThat(taughtSessions.countByCatalogItemId(figma.getId())).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(body(usage(coordinator, tenant, figma)), "$.sessionsTaught")).isEqualTo(1);
  }
}
