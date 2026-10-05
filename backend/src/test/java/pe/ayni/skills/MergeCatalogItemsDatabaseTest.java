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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.skills.domain.model.AccreditationPath;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.LearningInterest;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.LearningInterestRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;
import pe.ayni.skills.infrastructure.TaughtSessionRepository;

/**
 * {@code POST /api/v1/coordinator/catalog/{id}/merge} over HTTP against a real PostgreSQL: what
 * joining two items leaves in every table that holds an item, and what is refused.
 *
 * <p>Every test works with items of its own, so what other tests left does not count.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class MergeCatalogItemsDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final UUID coordinator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private OfferedSkillRepository offeredSkills;
  @Autowired private LearningInterestRepository interests;
  @Autowired private SkillProposalRepository proposals;
  @Autowired private TaughtSessionRepository taughtSessions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents applicationEvents;
  @MockitoBean private IdentityApi identity;

  private String tenant;
  private UUID category;
  private CatalogItem duplicate;
  private CatalogItem kept;

  @BeforeEach
  void aUniversityAndTwoToolsThatAreTheSame() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    category = categories.save(new Category(UUID.randomUUID(), "Category " + tenant, (short) 0)).getId();
    when(identity.requireUser(coordinator))
        .thenReturn(new UserView(coordinator, tenant, UserRole.COORDINATOR, "c@u.pe", null, "C", null, null, null));
    when(identity.requireUser(student))
        .thenReturn(new UserView(student, tenant, UserRole.STUDENT, "s@u.pe", "U1", "S", null, null, null));
    duplicate = tool();
    kept = tool();
  }

  private CatalogItem tool() {
    return catalogItems.save(
        new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, category, "Tool " + UUID.randomUUID(), null, null, NOW));
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

  private OfferedSkill enabledBy(String tenantCode, UUID tutor, CatalogItem item) {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), tenantCode, tutor, item.getId(), NOW);
    skill.approveByReviewedEvidence(NOW);
    return offeredSkills.save(skill);
  }

  private HttpHeaders headers(UUID user, String tenantCode) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", tenantCode);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private ResultActions merge(UUID user, String tenantCode, UUID removedId, String json) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/catalog/" + removedId + "/merge")
            .headers(headers(user, tenantCode))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions merge(CatalogItem removed, CatalogItem into) throws Exception {
    return merge(coordinator, tenant, removed.getId(), "{\"intoCatalogItemId\":\"%s\"}".formatted(into.getId()));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("the offers and accreditations of the duplicate hold on the item that stays, and the search is told")
  void theOffersHoldOnTheItemThatStays() throws Exception {
    String other = "T" + UUID.randomUUID().toString().substring(0, 8);
    OfferedSkill mine = enabledBy(tenant, UUID.randomUUID(), duplicate);
    OfferedSkill theirs = enabledBy(other, UUID.randomUUID(), duplicate);
    OfferedSkill pending =
        offeredSkills.save(OfferedSkill.requestValidation(UUID.randomUUID(), tenant, UUID.randomUUID(), duplicate.getId(), NOW));
    OfferedSkill alreadyOnKept = enabledBy(tenant, UUID.randomUUID(), kept);

    String answer = body(merge(duplicate, kept).andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(answer, "$.removedCatalogItemId")).isEqualTo(duplicate.getId().toString());
    assertThat(JsonPath.<String>read(answer, "$.keptCatalogItemId")).isEqualTo(kept.getId().toString());
    assertThat(JsonPath.<Integer>read(answer, "$.offersMoved")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(answer, "$.offersWithdrawn")).isZero();
    for (OfferedSkill moved : List.of(mine, theirs)) {
      OfferedSkill read = offeredSkills.findById(moved.getId()).orElseThrow();
      assertThat(read.getCatalogItemId()).isEqualTo(kept.getId());
      assertThat(read.getStatus()).isEqualTo(OfferedSkillStatus.ENABLED);
      assertThat(read.getAccreditationPath()).isEqualTo(AccreditationPath.REVIEWED_EVIDENCE);
    }
    OfferedSkill pendingRead = offeredSkills.findById(pending.getId()).orElseThrow();
    assertThat(pendingRead.getCatalogItemId()).isEqualTo(kept.getId());
    assertThat(pendingRead.getStatus()).isEqualTo(OfferedSkillStatus.PENDING);
    assertThat(offeredSkills.findById(alreadyOnKept.getId()).orElseThrow().getCatalogItemId()).isEqualTo(kept.getId());
    assertThat(catalogItems.findById(duplicate.getId()).orElseThrow().isActive()).isFalse();
    assertThat(catalogItems.findById(kept.getId()).orElseThrow().isActive()).isTrue();

    assertThat(applicationEvents.stream(SkillWithdrawn.class).filter(e -> e.catalogItemId().equals(duplicate.getId())))
        .extracting(SkillWithdrawn::tenantId, SkillWithdrawn::tutorId)
        .containsExactlyInAnyOrder(Tuple.tuple(tenant, mine.getTutorId()), Tuple.tuple(other, theirs.getTutorId()));
    assertThat(applicationEvents.stream(SkillEnabled.class).filter(e -> e.catalogItemId().equals(kept.getId())))
        .extracting(SkillEnabled::tenantId, SkillEnabled::tutorId)
        .containsExactlyInAnyOrder(Tuple.tuple(tenant, mine.getTutorId()), Tuple.tuple(other, theirs.getTutorId()));

    String usage =
        body(
            mockMvc
                .perform(get("/api/v1/coordinator/catalog/" + kept.getId() + "/usage").headers(headers(coordinator, tenant)))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<Integer>read(usage, "$.tutorsOffering")).isEqualTo(3);
  }

  @Test
  @DisplayName("a tutor who held both keeps the offer on the item that stays and the other is withdrawn")
  void aTutorWhoHeldBothKeepsTheOneThatStays() throws Exception {
    UUID tutor = UUID.randomUUID();
    OfferedSkill onDuplicate = enabledBy(tenant, tutor, duplicate);
    OfferedSkill onKept = enabledBy(tenant, tutor, kept);

    String answer = body(merge(duplicate, kept).andExpect(status().isOk()));

    assertThat(JsonPath.<Integer>read(answer, "$.offersMoved")).isZero();
    assertThat(JsonPath.<Integer>read(answer, "$.offersWithdrawn")).isEqualTo(1);
    assertThat(offeredSkills.findById(onKept.getId()).orElseThrow().isEnabled()).isTrue();
    OfferedSkill leftBehind = offeredSkills.findById(onDuplicate.getId()).orElseThrow();
    assertThat(leftBehind.getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
    assertThat(leftBehind.getCatalogItemId()).isEqualTo(duplicate.getId());
    assertThat(applicationEvents.stream(SkillWithdrawn.class).filter(e -> e.catalogItemId().equals(duplicate.getId())))
        .hasSize(1);
    assertThat(applicationEvents.stream(SkillEnabled.class).filter(e -> e.catalogItemId().equals(kept.getId()))).isEmpty();
  }

  @Test
  @DisplayName("learning interests follow, and a student interested in both is left with one")
  void learningInterestsFollow() throws Exception {
    UUID inBoth = UUID.randomUUID();
    UUID onlyInDuplicate = UUID.randomUUID();
    interests.save(new LearningInterest(UUID.randomUUID(), tenant, inBoth, duplicate.getId(), NOW));
    interests.save(new LearningInterest(UUID.randomUUID(), tenant, inBoth, kept.getId(), NOW));
    interests.save(new LearningInterest(UUID.randomUUID(), tenant, onlyInDuplicate, duplicate.getId(), NOW));
    // The same student id in another university is another person.
    String other = "T" + UUID.randomUUID().toString().substring(0, 8);
    interests.save(new LearningInterest(UUID.randomUUID(), other, inBoth, duplicate.getId(), NOW));

    String answer = body(merge(duplicate, kept).andExpect(status().isOk()));

    assertThat(JsonPath.<Integer>read(answer, "$.interestsDropped")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(answer, "$.interestsMoved")).isEqualTo(2);
    assertThat(interestsIn(kept)).containsExactlyInAnyOrder(Tuple.tuple(tenant, inBoth), Tuple.tuple(tenant, onlyInDuplicate), Tuple.tuple(other, inBoth));
    assertThat(interestsIn(duplicate)).isEmpty();
  }

  private List<Tuple> interestsIn(CatalogItem item) {
    return jdbc
        .queryForList("select tenant_id, student_id from skills.learning_interests where catalog_item_id = ?", item.getId())
        .stream()
        .map(row -> Tuple.tuple(row.get("tenant_id"), row.get("student_id")))
        .toList();
  }

  @Test
  @DisplayName("the proposals that ended in the duplicate and the sessions taught on it follow")
  void proposalsAndSessionsFollow() throws Exception {
    SkillProposal proposal = SkillProposal.propose(UUID.randomUUID(), tenant, student, category, "Dup " + UUID.randomUUID(), null, NOW);
    proposal.mergeInto(coordinator, duplicate.getId(), null, NOW);
    proposals.save(proposal);
    taughtSessions.recordIfNew(UUID.randomUUID(), tenant, duplicate.getId(), UUID.randomUUID(), NOW);
    taughtSessions.recordIfNew(UUID.randomUUID(), tenant, duplicate.getId(), UUID.randomUUID(), NOW);
    taughtSessions.recordIfNew(UUID.randomUUID(), tenant, kept.getId(), UUID.randomUUID(), NOW);

    String answer = body(merge(duplicate, kept).andExpect(status().isOk()));

    assertThat(JsonPath.<Integer>read(answer, "$.proposalsRepointed")).isEqualTo(1);
    assertThat(JsonPath.<Integer>read(answer, "$.sessionsCarriedOver")).isEqualTo(2);
    assertThat(proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow().getCatalogItemId()).isEqualTo(kept.getId());
    assertThat(taughtSessions.countByCatalogItemId(kept.getId())).isEqualTo(3);
    assertThat(taughtSessions.countByCatalogItemId(duplicate.getId())).isZero();
  }

  @Test
  @DisplayName("the duplicate leaves the catalogue and the one that stays is still there")
  void theDuplicateLeavesTheCatalogue() throws Exception {
    merge(duplicate, kept).andExpect(status().isOk());

    String catalogue =
        body(
            mockMvc
                .perform(get("/api/v1/catalog").header("X-Tenant-Id", tenant).param("category", category.toString()))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(catalogue, "$.items[*].id")).containsExactly(kept.getId().toString());
  }

  @Test
  @DisplayName("two courses of the university are joined, and a course never joins a tool")
  void twoCoursesAreJoinedAndACourseNeverJoinsATool() throws Exception {
    CatalogItem oldCourse = course(tenant);
    CatalogItem newCourse = course(tenant);
    OfferedSkill offer = enabledBy(tenant, UUID.randomUUID(), oldCourse);

    merge(oldCourse, duplicate).andExpect(status().isBadRequest());
    merge(duplicate, newCourse).andExpect(status().isBadRequest());
    merge(oldCourse, newCourse).andExpect(status().isOk());

    assertThat(offeredSkills.findById(offer.getId()).orElseThrow().getCatalogItemId()).isEqualTo(newCourse.getId());
    assertThat(catalogItems.findById(oldCourse.getId()).orElseThrow().isActive()).isFalse();
    assertThat(catalogItems.findById(duplicate.getId()).orElseThrow().isActive()).isTrue();
  }

  @Test
  @DisplayName("the same item twice, a duplicate already retired and a target retired are refused")
  void incoherentJoinsAreRefused() throws Exception {
    merge(duplicate, duplicate).andExpect(status().isBadRequest());

    CatalogItem retired = tool();
    retired.retire();
    catalogItems.save(retired);
    merge(duplicate, retired).andExpect(status().isBadRequest());
    merge(retired, duplicate).andExpect(status().isConflict());

    assertThat(catalogItems.findById(duplicate.getId()).orElseThrow().isActive()).isTrue();
  }

  @Test
  @DisplayName("a missing or unknown target, a student, and a course of another university are refused")
  void unknownItemsAndStudentsAreRefused() throws Exception {
    CatalogItem theirs = course("T" + UUID.randomUUID().toString().substring(0, 8));

    merge(coordinator, tenant, duplicate.getId(), "{}").andExpect(status().isBadRequest());
    merge(coordinator, tenant, duplicate.getId(), "{\"intoCatalogItemId\":\"%s\"}".formatted(UUID.randomUUID()))
        .andExpect(status().isNotFound());
    merge(coordinator, tenant, UUID.randomUUID(), "{\"intoCatalogItemId\":\"%s\"}".formatted(kept.getId()))
        .andExpect(status().isNotFound());
    merge(coordinator, tenant, theirs.getId(), "{\"intoCatalogItemId\":\"%s\"}".formatted(kept.getId()))
        .andExpect(status().isNotFound());
    merge(student, tenant, duplicate.getId(), "{\"intoCatalogItemId\":\"%s\"}".formatted(kept.getId()))
        .andExpect(status().isForbidden());

    assertThat(catalogItems.findById(duplicate.getId()).orElseThrow().isActive()).isTrue();
  }
}
