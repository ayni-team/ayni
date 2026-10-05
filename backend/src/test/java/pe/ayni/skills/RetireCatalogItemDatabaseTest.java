package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * {@code POST /api/v1/coordinator/catalog/{id}/retire} over HTTP against a real PostgreSQL: what a
 * retirement leaves in the catalogue, in the offers and in what it announces, and what is refused.
 *
 * <p>Every test works with an item of its own, so what other tests left does not count.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class RetireCatalogItemDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final UUID coordinator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private OfferedSkillRepository offeredSkills;
  @Autowired private ApplicationEvents applicationEvents;
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

  private CatalogItem tool(String name) {
    return catalogItems.save(
        new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, category, name, null, null, NOW));
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

  private OfferedSkill enabledBy(String tenantCode, CatalogItem item) {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), tenantCode, UUID.randomUUID(), item.getId(), NOW);
    skill.approveByReviewedEvidence(NOW);
    return offeredSkills.save(skill);
  }

  private HttpHeaders headers(UUID user, String tenantCode) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", tenantCode);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private ResultActions retire(UUID user, String tenantCode, UUID itemId, String json) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/catalog/" + itemId + "/retire")
            .headers(headers(user, tenantCode))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions retire(UUID itemId, long confirmed) throws Exception {
    return retire(coordinator, tenant, itemId, "{\"confirmedTutors\":%d}".formatted(confirmed));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private List<SkillWithdrawn> withdrawnFrom(CatalogItem item) {
    return applicationEvents.stream(SkillWithdrawn.class)
        .filter(event -> event.catalogItemId().equals(item.getId()))
        .toList();
  }

  @Test
  @DisplayName("retiring a tool withdraws every offer, in every university, and announces each one")
  void retiringAToolWithdrawsEveryOffer() throws Exception {
    CatalogItem figma = tool("Figma " + UUID.randomUUID());
    String other = "T" + UUID.randomUUID().toString().substring(0, 8);
    OfferedSkill mine = enabledBy(tenant, figma);
    OfferedSkill theirs = enabledBy(other, figma);

    String answer = body(retire(figma.getId(), 2).andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(answer, "$.status")).isEqualTo("RETIRED");
    assertThat(JsonPath.<Integer>read(answer, "$.tutorsAffected")).isEqualTo(2);
    assertThat(catalogItems.findById(figma.getId()).orElseThrow().isActive()).isFalse();
    assertThat(offeredSkills.findById(mine.getId()).orElseThrow().getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
    assertThat(offeredSkills.findById(theirs.getId()).orElseThrow().getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
    assertThat(withdrawnFrom(figma))
        .extracting(SkillWithdrawn::tenantId, SkillWithdrawn::tutorId)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(tenant, mine.getTutorId()),
            org.assertj.core.groups.Tuple.tuple(other, theirs.getTutorId()));
  }

  @Test
  @DisplayName("a retired item leaves the catalogue and the similar skills, and cannot be offered")
  void aRetiredItemLeavesTheCatalogueAndCannotBeOffered() throws Exception {
    // Letters only and unlike any other name, because the similar skills compare with the whole catalogue.
    StringBuilder letters = new StringBuilder("qz");
    for (int i = 0; i < 12; i++) {
      letters.append((char) ('a' + java.util.concurrent.ThreadLocalRandom.current().nextInt(26)));
    }
    CatalogItem figma = tool(letters.toString());
    retire(figma.getId(), 0).andExpect(status().isOk());

    String catalogue =
        body(
            mockMvc
                .perform(get("/api/v1/catalog").header("X-Tenant-Id", tenant).param("q", figma.getName()))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(catalogue, "$.items[*].name")).isEmpty();
    String similar =
        body(
            mockMvc
                .perform(get("/api/v1/catalog/similar").header("X-Tenant-Id", tenant).param("name", figma.getName()))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(similar, "$[*].name")).isEmpty();
    mockMvc
        .perform(
            post("/api/v1/tutor/skills")
                .headers(headers(student, tenant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogItemId\":\"" + figma.getId() + "\"}"))
        .andExpect(status().isBadRequest());
    String usage =
        body(
            mockMvc
                .perform(get("/api/v1/coordinator/catalog/" + figma.getId() + "/usage").headers(headers(coordinator, tenant)))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<String>read(usage, "$.item.status")).isEqualTo("RETIRED");
    assertThat(JsonPath.<Integer>read(usage, "$.tutorsOffering")).isZero();
  }

  @Test
  @DisplayName("the moderator reads how many tutors are affected before confirming, and confirms with it")
  void theModeratorReadsTheNumberBeforeConfirming() throws Exception {
    CatalogItem figma = tool("Figma " + UUID.randomUUID());
    enabledBy(tenant, figma);
    enabledBy(tenant, figma);
    enabledBy(tenant, figma);

    String usage =
        body(
            mockMvc
                .perform(get("/api/v1/coordinator/catalog/" + figma.getId() + "/usage").headers(headers(coordinator, tenant)))
                .andExpect(status().isOk()));
    int affected = JsonPath.<Integer>read(usage, "$.tutorsOffering");

    String answer = body(retire(figma.getId(), affected).andExpect(status().isOk()));

    assertThat(affected).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(answer, "$.tutorsAffected")).isEqualTo(3);
  }

  @Test
  @DisplayName("a confirmation that is not the number of tutors retires nothing and says the real number")
  void aWrongConfirmationRetiresNothing() throws Exception {
    CatalogItem figma = tool("Figma " + UUID.randomUUID());
    OfferedSkill offer = enabledBy(tenant, figma);

    String refusal = body(retire(figma.getId(), 5).andExpect(status().isConflict()));

    assertThat(JsonPath.<String>read(refusal, "$.message")).contains("1 tutors offer this skill now, not 5");
    assertThat(catalogItems.findById(figma.getId()).orElseThrow().isActive()).isTrue();
    assertThat(offeredSkills.findById(offer.getId()).orElseThrow().isEnabled()).isTrue();
    assertThat(withdrawnFrom(figma)).isEmpty();
  }

  @Test
  @DisplayName("an item retired once cannot be retired again, and nothing is announced twice")
  void anItemRetiredOnceCannotBeRetiredAgain() throws Exception {
    CatalogItem figma = tool("Figma " + UUID.randomUUID());
    enabledBy(tenant, figma);
    retire(figma.getId(), 1).andExpect(status().isOk());

    retire(figma.getId(), 0).andExpect(status().isConflict());

    assertThat(withdrawnFrom(figma)).hasSize(1);
  }

  @Test
  @DisplayName("a missing, negative or unreadable confirmation is refused")
  void aBadConfirmationIsRefused() throws Exception {
    CatalogItem figma = tool("Figma " + UUID.randomUUID());

    retire(coordinator, tenant, figma.getId(), "{}").andExpect(status().isBadRequest());
    retire(coordinator, tenant, figma.getId(), "{\"confirmedTutors\":-1}").andExpect(status().isBadRequest());
    retire(coordinator, tenant, figma.getId(), "{\"confirmedTutors\":\"many\"}").andExpect(status().isBadRequest());

    assertThat(catalogItems.findById(figma.getId()).orElseThrow().isActive()).isTrue();
  }

  @Test
  @DisplayName("a student cannot retire an item, a course of another university is not found, and neither is an unknown item")
  void aStudentCannotRetireAndOtherUniversitiesDoNotFindIt() throws Exception {
    CatalogItem figma = tool("Figma " + UUID.randomUUID());
    CatalogItem theirs = course("T" + UUID.randomUUID().toString().substring(0, 8));

    retire(student, tenant, figma.getId(), "{\"confirmedTutors\":0}").andExpect(status().isForbidden());
    retire(theirs.getId(), 0).andExpect(status().isNotFound());
    retire(UUID.randomUUID(), 0).andExpect(status().isNotFound());

    assertThat(catalogItems.findById(figma.getId()).orElseThrow().isActive()).isTrue();
    assertThat(catalogItems.findById(theirs.getId()).orElseThrow().isActive()).isTrue();
  }

  @Test
  @DisplayName("a course of the university is retired for that university only")
  void aCourseIsRetiredForItsUniversity() throws Exception {
    CatalogItem course = course(tenant);
    OfferedSkill offer = enabledBy(tenant, course);

    retire(course.getId(), 1).andExpect(status().isOk());

    assertThat(catalogItems.findById(course.getId()).orElseThrow().isActive()).isFalse();
    assertThat(offeredSkills.findById(offer.getId()).orElseThrow().getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
  }
}
