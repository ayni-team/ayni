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
 * {@code POST /api/v1/coordinator/skill-proposals/{id}/decision} over HTTP against a real
 * PostgreSQL: what each decision leaves in the catalogue and in the proposal, and what is refused.
 *
 * <p>Every test works in a university of its own and a name of random letters, so the catalogue and
 * the proposals other tests left do not count.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProposalDecisionDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final UUID coordinator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private SkillProposalRepository proposals;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;

  private String tenant;
  private UUID category;
  private String word;

  @BeforeEach
  void aUniversityAndAWordOfItsOwn() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    category = categories.save(new Category(UUID.randomUUID(), "Design " + tenant, (short) 0)).getId();
    StringBuilder letters = new StringBuilder("qz");
    for (int i = 0; i < 12; i++) {
      letters.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
    }
    word = letters.toString();
    when(identity.requireUser(coordinator))
        .thenReturn(new UserView(coordinator, tenant, UserRole.COORDINATOR, "c@u.pe", null, "Carla Ríos", null, null, null));
    when(identity.requireUser(student))
        .thenReturn(new UserView(student, tenant, UserRole.STUDENT, "s@u.pe", "U1", "Ana Torres", "Software", "5", null));
  }

  private SkillProposal proposal(String name) {
    return proposals.save(SkillProposal.propose(UUID.randomUUID(), tenant, student, category, name, "A tool", NOW));
  }

  private CatalogItem tool(String name) {
    return catalogItems.save(
        new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, category, name, null, null, NOW));
  }

  private HttpHeaders headers(UUID user, String tenantCode) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", tenantCode);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private ResultActions decide(UUID user, String tenantCode, UUID proposalId, String json) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/skill-proposals/" + proposalId + "/decision")
            .headers(headers(user, tenantCode))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions decide(UUID proposalId, String json) throws Exception {
    return decide(coordinator, tenant, proposalId, json);
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private int itemsNamed(String name) {
    return jdbc.queryForObject(
        "select count(*) from skills.catalog_items where lower(name) = lower(?)", Integer.class, name);
  }

  @Test
  @DisplayName("approving adds a global item that every university finds in its catalogue")
  void approvingAddsAGlobalItemEveryUniversityFinds() throws Exception {
    SkillProposal proposal = proposal(word);

    String answer = body(decide(proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(answer, "$.status")).isEqualTo("APPROVED");
    assertThat(JsonPath.<String>read(answer, "$.catalogItem.name")).isEqualTo(word);
    assertThat(JsonPath.<String>read(answer, "$.catalogItem.scope")).isEqualTo("GLOBAL");
    SkillProposal stored = proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(ProposalStatus.APPROVED);
    assertThat(stored.getResolvedBy()).isEqualTo(coordinator);
    assertThat(stored.getCatalogItemId().toString()).isEqualTo(JsonPath.<String>read(answer, "$.catalogItem.id"));
    CatalogItem created = catalogItems.findById(stored.getCatalogItemId()).orElseThrow();
    assertThat(created.getTenantId()).isNull();
    assertThat(created.getDescription()).isEqualTo("A tool");
    assertThat(created.getCategoryId()).isEqualTo(category);

    String otherTenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    String catalogue =
        body(
            mockMvc
                .perform(get("/api/v1/catalog").header("X-Tenant-Id", otherTenant).param("q", word))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(catalogue, "$.items[*].name")).containsExactly(word);
  }

  @Test
  @DisplayName("joining creates no item and the student reads the one it was joined to")
  void joiningCreatesNoItemAndTheStudentReadsTheOne() throws Exception {
    CatalogItem nodeJs = tool(word + ".js");
    SkillProposal proposal = proposal(word + "JS Advanced");
    int before = itemsNamed(word + "JS Advanced");

    String answer =
        body(
            decide(
                    proposal.getId(),
                    "{\"decision\":\"MERGE\",\"catalogItemId\":\"%s\",\"reason\":\"It is Node.js\"}"
                        .formatted(nodeJs.getId()))
                .andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(answer, "$.status")).isEqualTo("MERGED");
    assertThat(JsonPath.<String>read(answer, "$.catalogItem.id")).isEqualTo(nodeJs.getId().toString());
    assertThat(itemsNamed(word + "JS Advanced")).isEqualTo(before);
    String mine =
        body(
            mockMvc
                .perform(get("/api/v1/tutor/skills/proposals").headers(headers(student, tenant)))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<String>read(mine, "$[0].status")).isEqualTo("MERGED");
    assertThat(JsonPath.<String>read(mine, "$[0].catalogItemId")).isEqualTo(nodeJs.getId().toString());
  }

  @Test
  @DisplayName("rejecting leaves the reason the student reads, and no item")
  void rejectingLeavesTheReasonTheStudentReads() throws Exception {
    SkillProposal proposal = proposal(word);

    String answer =
        body(
            decide(proposal.getId(), "{\"decision\":\"REJECT\",\"reason\":\"Not a tool we teach\"}")
                .andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(answer, "$.status")).isEqualTo("REJECTED");
    assertThat(JsonPath.<Object>read(answer, "$.catalogItem")).isNull();
    assertThat(itemsNamed(word)).isZero();
    String mine =
        body(
            mockMvc
                .perform(get("/api/v1/tutor/skills/proposals").headers(headers(student, tenant)))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<String>read(mine, "$[0].status")).isEqualTo("REJECTED");
    assertThat(JsonPath.<String>read(mine, "$[0].decisionReason")).isEqualTo("Not a tool we teach");
  }

  @Test
  @DisplayName("the decision shows in the history with who, when and how")
  void theDecisionShowsInTheHistory() throws Exception {
    SkillProposal proposal = proposal(word);
    decide(proposal.getId(), "{\"decision\":\"REJECT\",\"reason\":\"Not a tool we teach\"}")
        .andExpect(status().isOk());

    String history =
        body(
            mockMvc
                .perform(
                    get("/api/v1/coordinator/skill-proposals")
                        .param("status", "REJECTED")
                        .headers(headers(coordinator, tenant)))
                .andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(history, "$.items[0].resolution.how")).isEqualTo("REJECTED");
    assertThat(JsonPath.<String>read(history, "$.items[0].resolution.resolvedBy.fullName")).isEqualTo("Carla Ríos");
    assertThat(JsonPath.<String>read(history, "$.items[0].resolution.resolvedAt")).isNotBlank();
    assertThat(JsonPath.<String>read(history, "$.items[0].resolution.reason")).isEqualTo("Not a tool we teach");
  }

  @Test
  @DisplayName("a rejection without a reason, a merge without an item and an item with another decision are refused")
  void incompleteDecisionsAreRefused() throws Exception {
    SkillProposal proposal = proposal(word);
    CatalogItem item = tool(word + " other");

    decide(proposal.getId(), "{\"decision\":\"REJECT\"}").andExpect(status().isBadRequest());
    decide(proposal.getId(), "{\"decision\":\"REJECT\",\"reason\":\"   \"}").andExpect(status().isBadRequest());
    decide(proposal.getId(), "{\"decision\":\"MERGE\"}").andExpect(status().isBadRequest());
    decide(proposal.getId(), "{\"decision\":\"APPROVE\",\"catalogItemId\":\"%s\"}".formatted(item.getId()))
        .andExpect(status().isBadRequest());
    decide(proposal.getId(), "{\"decision\":\"MAYBE\"}").andExpect(status().isBadRequest());
    decide(proposal.getId(), "{}").andExpect(status().isBadRequest());

    assertThat(proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow().isWaiting()).isTrue();
  }

  @Test
  @DisplayName("an item to join that does not exist is not found, and a retired one is refused")
  void anItemToJoinThatDoesNotExistIsNotFound() throws Exception {
    SkillProposal proposal = proposal(word);
    CatalogItem retired = tool(word + " retired");
    retired.retire();
    catalogItems.save(retired);

    decide(proposal.getId(), "{\"decision\":\"MERGE\",\"catalogItemId\":\"%s\"}".formatted(UUID.randomUUID()))
        .andExpect(status().isNotFound());
    decide(proposal.getId(), "{\"decision\":\"MERGE\",\"catalogItemId\":\"%s\"}".formatted(retired.getId()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("a proposal resolved once is a conflict the second time, and nothing is added")
  void aProposalResolvedOnceIsAConflict() throws Exception {
    SkillProposal proposal = proposal(word);
    decide(proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());

    decide(proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isConflict());
    decide(proposal.getId(), "{\"decision\":\"REJECT\",\"reason\":\"Changed my mind\"}")
        .andExpect(status().isConflict());

    assertThat(itemsNamed(word)).isEqualTo(1);
  }

  @Test
  @DisplayName("approving a name the catalogue already has is a conflict")
  void approvingANameTheCatalogueAlreadyHasIsAConflict() throws Exception {
    tool(word);
    SkillProposal proposal = proposal(word.toUpperCase());

    decide(proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isConflict());

    assertThat(itemsNamed(word)).isEqualTo(1);
    assertThat(proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow().isWaiting()).isTrue();
  }

  @Test
  @DisplayName("a student cannot decide, and a proposal of another university is not found")
  void aStudentCannotDecideAndAnotherUniversityDoesNotFindIt() throws Exception {
    SkillProposal proposal = proposal(word);
    String other = "T" + UUID.randomUUID().toString().substring(0, 8);

    decide(student, tenant, proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isForbidden());
    decide(coordinator, other, proposal.getId(), "{\"decision\":\"APPROVE\"}").andExpect(status().isNotFound());
    decide(UUID.randomUUID(), "{\"decision\":\"APPROVE\"}").andExpect(status().isNotFound());

    assertThat(itemsNamed(word)).isZero();
    assertThat(proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow().isWaiting()).isTrue();
  }
}
