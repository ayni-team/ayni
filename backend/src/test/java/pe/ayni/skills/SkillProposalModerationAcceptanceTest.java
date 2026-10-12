package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/**
 * US43, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code
 * src/test/resources/features/US43-moderate-proposed-skills.feature}, with the same names. The
 * identity of each person is stubbed, since identity is another module; everything else is the real
 * application.
 *
 * <p>Each test works in a university of its own and a name of random letters, so the queue and the
 * catalogue other tests left do not count.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SkillProposalModerationAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final UUID student = UUID.randomUUID();
  private final UUID coordinator = UUID.randomUUID();
  private final UUID otherCoordinator = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private SkillProposalRepository proposals;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;

  private String tenant;
  private UUID category;
  private String word;

  /** Background: a university, a student and a coordinator of it, a category and a word of its own. */
  @BeforeEach
  void aUniversityWithAStudentAndACoordinator() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    category = categories.save(new Category(UUID.randomUUID(), "Design " + tenant, (short) 0)).getId();
    StringBuilder letters = new StringBuilder("qz");
    for (int i = 0; i < 12; i++) {
      letters.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
    }
    word = letters.toString();
    Set<UUID> coordinators = Set.of(coordinator, otherCoordinator);
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              boolean moderator = coordinators.contains(id);
              return new UserView(
                  id,
                  tenant,
                  moderator ? UserRole.COORDINATOR : UserRole.STUDENT,
                  "someone@upc.edu.pe",
                  moderator ? null : "U202310949",
                  id.equals(coordinator) ? "Carla Ríos" : moderator ? "Mario Paz" : "Ana Torres",
                  moderator ? null : "Software Engineering",
                  null,
                  null);
            });
  }

  @Test
  @DisplayName("Queue of pending proposals")
  void queueOfPendingProposals() throws Exception {

    proposal(word + " second", NOW.plusSeconds(60));
    proposal(word + " first", NOW);

    String queue = body(list(coordinator, tenant, "").andExpect(status().isOk()));

    assertThat(JsonPath.<List<String>>read(queue, "$.items[*].name"))
        .containsExactly(word + " first", word + " second");
    assertThat(JsonPath.<String>read(queue, "$.items[0].category.name")).isEqualTo("Design " + tenant);
    assertThat(JsonPath.<String>read(queue, "$.items[0].proposer.fullName")).isEqualTo("Ana Torres");
    assertThat(JsonPath.<String>read(queue, "$.items[0].proposedAt")).startsWith("2026-10-05T09:00:00");
    assertThat(JsonPath.<String>read(queue, "$.items[0].status")).isEqualTo("PROPOSED");
  }

  @Test
  @DisplayName("Approved proposal")
  void approvedProposal() throws Exception {

    SkillProposal proposal = proposal(word, NOW);

    String answer = body(decide(coordinator, tenant, proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(answer, "$.status")).isEqualTo("APPROVED");
    assertThat(JsonPath.<String>read(answer, "$.catalogItem.scope")).isEqualTo("GLOBAL");
    String otherTenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    String catalogue =
        body(
            mockMvc
                .perform(get("/api/v1/catalog").header("X-Tenant-Id", otherTenant).param("q", word))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(catalogue, "$.items[*].name")).containsExactly(word);
    assertThat(JsonPath.<List<String>>read(catalogue, "$.items[*].scope")).containsExactly("GLOBAL");
    assertThat(itemsNamed(word)).isEqualTo(1);
  }

  @Test
  @DisplayName("Proposal joined to an existing skill")
  void proposalJoinedToAnExistingSkill() throws Exception {

    CatalogItem nodeJs = tool(word + ".js");
    SkillProposal proposal = proposal(word + "JS Advanced", NOW);
    int itemsBefore = itemsInCategory();

    String detail =
        body(
            mockMvc
                .perform(
                    get("/api/v1/coordinator/skill-proposals/" + proposal.getId())
                        .headers(headers(coordinator, tenant)))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(detail, "$.similar[*].id")).containsExactly(nodeJs.getId().toString());

    String answer =
        body(
            decide(
                    coordinator,
                    tenant,
                    proposal.getId(),
                    "{\"decision\":\"MERGE\",\"catalogItemId\":\"%s\"}".formatted(nodeJs.getId()))
                .andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(answer, "$.status")).isEqualTo("MERGED");
    assertThat(itemsInCategory()).isEqualTo(itemsBefore);
    String mine = body(proposalsOf(student, tenant));
    assertThat(JsonPath.<String>read(mine, "$[0].status")).isEqualTo("MERGED");
    assertThat(JsonPath.<String>read(mine, "$[0].catalogItemId")).isEqualTo(nodeJs.getId().toString());
  }

  @Test
  @DisplayName("Rejected proposal")
  void rejectedProposal() throws Exception {

    SkillProposal proposal = proposal(word, NOW);

    decide(coordinator, tenant, proposal.getId(), "{\"decision\":\"REJECT\",\"reason\":\"Not a tool we teach\"}")
        .andExpect(status().isOk());

    String mine = body(proposalsOf(student, tenant));
    assertThat(JsonPath.<String>read(mine, "$[0].status")).isEqualTo("REJECTED");
    assertThat(JsonPath.<String>read(mine, "$[0].decisionReason")).isEqualTo("Not a tool we teach");
    assertThat(itemsNamed(word)).isZero();
  }

  @Test
  @DisplayName("Traceability of the decision")
  void traceabilityOfTheDecision() throws Exception {

    SkillProposal rejected = proposal(word + " one", NOW);
    SkillProposal approved = proposal(word + " two", NOW);
    decide(otherCoordinator, tenant, rejected.getId(), "{\"decision\":\"REJECT\",\"reason\":\"Not now\"}")
        .andExpect(status().isOk());
    decide(coordinator, tenant, approved.getId(), "{\"decision\":\"APPROVE\",\"reason\":\"Widely used\"}")
        .andExpect(status().isOk());

    String history = body(list(coordinator, tenant, "?status=APPROVED").andExpect(status().isOk()));
    assertThat(JsonPath.<String>read(history, "$.items[0].name")).isEqualTo(word + " two");
    assertThat(JsonPath.<String>read(history, "$.items[0].resolution.how")).isEqualTo("APPROVED");
    assertThat(JsonPath.<String>read(history, "$.items[0].resolution.resolvedBy.fullName")).isEqualTo("Carla Ríos");
    assertThat(JsonPath.<String>read(history, "$.items[0].resolution.resolvedAt")).isNotBlank();
    assertThat(JsonPath.<String>read(history, "$.items[0].resolution.reason")).isEqualTo("Widely used");
    assertThat(JsonPath.<String>read(history, "$.items[0].resolution.catalogItem.name")).isEqualTo(word + " two");

    String other = body(list(coordinator, tenant, "?status=REJECTED").andExpect(status().isOk()));
    assertThat(JsonPath.<String>read(other, "$.items[0].resolution.how")).isEqualTo("REJECTED");
    assertThat(JsonPath.<String>read(other, "$.items[0].resolution.resolvedBy.fullName")).isEqualTo("Mario Paz");
    assertThat(JsonPath.<Object>read(other, "$.items[0].resolution.catalogItem")).isNull();

    String queue = body(list(coordinator, tenant, "").andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(queue, "$.items[*].name")).isEmpty();
  }

  @Test
  @DisplayName("A rejection needs a reason")
  void aRejectionNeedsAReason() throws Exception {

    SkillProposal proposal = proposal(word, NOW);

    decide(coordinator, tenant, proposal.getId(), "{\"decision\":\"REJECT\"}").andExpect(status().isBadRequest());

    assertThat(proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow().isWaiting()).isTrue();
  }

  @Test
  @DisplayName("A proposal already resolved cannot be resolved again")
  void aProposalAlreadyResolvedCannotBeResolvedAgain() throws Exception {

    SkillProposal proposal = proposal(word, NOW);
    decide(coordinator, tenant, proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());

    decide(coordinator, tenant, proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isConflict());
    decide(coordinator, tenant, proposal.getId(), "{\"decision\":\"REJECT\",\"reason\":\"Again\"}")
        .andExpect(status().isConflict());

    assertThat(itemsNamed(word)).isEqualTo(1);
    assertThat(proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow().getStatus())
        .isEqualTo(ProposalStatus.APPROVED);
  }

  @Test
  @DisplayName("A name the catalogue already has cannot be approved")
  void aNameTheCatalogueAlreadyHasCannotBeApproved() throws Exception {

    tool(word);
    SkillProposal proposal = proposal(word.toUpperCase(), NOW);

    decide(coordinator, tenant, proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isConflict());

    assertThat(itemsNamed(word)).isEqualTo(1);
    assertThat(proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow().isWaiting()).isTrue();
  }

  @Test
  @DisplayName("A student cannot moderate")
  void aStudentCannotModerate() throws Exception {

    SkillProposal proposal = proposal(word, NOW);

    list(student, tenant, "").andExpect(status().isForbidden());
    decide(student, tenant, proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isForbidden());

    assertThat(itemsNamed(word)).isZero();
    assertThat(proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow().isWaiting()).isTrue();
  }

  @Test
  @DisplayName("Another university does not see the proposals")
  void anotherUniversityDoesNotSeeTheProposals() throws Exception {

    SkillProposal proposal = proposal(word, NOW);
    String other = "T" + UUID.randomUUID().toString().substring(0, 8);

    String queue = body(list(otherCoordinator, other, "").andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(queue, "$.items[*].name")).isEmpty();
    decide(otherCoordinator, other, proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isNotFound());

    assertThat(proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow().isWaiting()).isTrue();
  }

  // ---- what each scenario needs -------------------------------------------------------------

  private HttpHeaders headers(UUID user, String tenantCode) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", tenantCode);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private SkillProposal proposal(String name, Instant at) {
    return proposals.save(SkillProposal.propose(UUID.randomUUID(), tenant, student, category, name, "A tool", at));
  }

  private CatalogItem tool(String name) {
    return catalogItems.save(
        new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, category, name, null, null, NOW));
  }

  private ResultActions list(UUID user, String tenantCode, String query) throws Exception {
    return mockMvc.perform(get("/api/v1/coordinator/skill-proposals" + query).headers(headers(user, tenantCode)));
  }

  private ResultActions decide(UUID user, String tenantCode, UUID proposalId, String json) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/skill-proposals/{id}/decision", proposalId)
            .headers(headers(user, tenantCode))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions proposalsOf(UUID user, String tenantCode) throws Exception {
    return mockMvc
        .perform(get("/api/v1/tutor/skills/proposals").headers(headers(user, tenantCode)))
        .andExpect(status().isOk());
  }

  private int itemsNamed(String name) {
    return jdbc.queryForObject(
        "select count(*) from skills.catalog_items where lower(name) = lower(?)", Integer.class, name);
  }

  private int itemsInCategory() {
    return jdbc.queryForObject(
        "select count(*) from skills.catalog_items where category_id = ?", Integer.class, category);
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }
}
