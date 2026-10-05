package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/**
 * {@code GET /api/v1/coordinator/skill-proposals} over HTTP against a real PostgreSQL: the queue,
 * the history, the order of both and the isolation between universities.
 *
 * <p>Every test works in a university of its own, so the proposals other tests left do not count.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModerationQueueDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final UUID coordinator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private SkillProposalRepository proposals;
  @MockitoBean private IdentityApi identity;

  private String tenant;
  private UUID category;

  @BeforeEach
  void aUniversityOfItsOwn() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    category = categories.save(new Category(UUID.randomUUID(), "Design " + tenant, (short) 0)).getId();
    when(identity.requireUser(coordinator)).thenReturn(person(coordinator, UserRole.COORDINATOR, "Carla Ríos"));
    when(identity.requireUser(student)).thenReturn(person(student, UserRole.STUDENT, "Ana Torres"));
  }

  private UserView person(UUID id, UserRole role, String name) {
    return new UserView(id, tenant, role, "x@upc.edu.pe", "U202310949", name, "Software Engineering", "5", null);
  }

  private SkillProposal propose(String name, Instant at) {
    return proposals.save(SkillProposal.propose(UUID.randomUUID(), tenant, student, category, name, "A tool", at));
  }

  private CatalogItem tool(String name) {
    return catalogItems.save(
        new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, category, name, null, null, NOW));
  }

  private ResultActions list(UUID asking, String tenantCode, String query) throws Exception {
    return mockMvc.perform(
        get("/api/v1/coordinator/skill-proposals" + query).headers(headers(asking, tenantCode)));
  }

  private HttpHeaders headers(UUID user, String tenantCode) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", tenantCode);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("the queue lists what waits, oldest first, with name, category, proposer and date")
  void theQueueListsWhatWaitsOldestFirst() throws Exception {
    propose("Newer tool", NOW.plusSeconds(60));
    propose("Older tool", NOW);

    String queue = body(list(coordinator, tenant, "").andExpect(status().isOk()));

    assertThat(JsonPath.<List<String>>read(queue, "$.items[*].name")).containsExactly("Older tool", "Newer tool");
    assertThat(JsonPath.<Integer>read(queue, "$.totalElements")).isEqualTo(2);
    assertThat(JsonPath.<String>read(queue, "$.items[0].category.name")).isEqualTo("Design " + tenant);
    assertThat(JsonPath.<String>read(queue, "$.items[0].proposer.fullName")).isEqualTo("Ana Torres");
    assertThat(JsonPath.<String>read(queue, "$.items[0].proposer.studentCode")).isEqualTo("U202310949");
    assertThat(JsonPath.<String>read(queue, "$.items[0].proposedAt")).startsWith("2026-10-05T09:00:00");
    assertThat(JsonPath.<String>read(queue, "$.items[0].status")).isEqualTo("PROPOSED");
    assertThat(JsonPath.<Object>read(queue, "$.items[0].resolution")).isNull();
  }

  @Test
  @DisplayName("the history lists what was resolved, newest first, with who, when and how")
  void theHistoryListsWhatWasResolvedNewestFirst() throws Exception {
    CatalogItem existing = tool("Node " + tenant);
    SkillProposal first = propose("First", NOW);
    SkillProposal second = propose("Second", NOW);
    propose("Still waiting", NOW);
    first.reject(coordinator, "Not a tool we teach", NOW.plusSeconds(10));
    second.mergeInto(coordinator, existing.getId(), "It is Node", NOW.plusSeconds(20));
    proposals.saveAll(List.of(first, second));

    String resolved = body(list(coordinator, tenant, "?status=MERGED").andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(resolved, "$.items[*].name")).containsExactly("Second");
    assertThat(JsonPath.<String>read(resolved, "$.items[0].resolution.how")).isEqualTo("MERGED");
    assertThat(JsonPath.<String>read(resolved, "$.items[0].resolution.resolvedBy.fullName")).isEqualTo("Carla Ríos");
    assertThat(JsonPath.<String>read(resolved, "$.items[0].resolution.resolvedAt")).startsWith("2026-10-05T09:00:20");
    assertThat(JsonPath.<String>read(resolved, "$.items[0].resolution.reason")).isEqualTo("It is Node");
    assertThat(JsonPath.<String>read(resolved, "$.items[0].resolution.catalogItem.id"))
        .isEqualTo(existing.getId().toString());

    String rejected = body(list(coordinator, tenant, "?status=REJECTED").andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(rejected, "$.items[*].name")).containsExactly("First");
    assertThat(JsonPath.<String>read(rejected, "$.items[0].resolution.reason")).isEqualTo("Not a tool we teach");
    assertThat(JsonPath.<Object>read(rejected, "$.items[0].resolution.catalogItem")).isNull();
  }

  @Test
  @DisplayName("the history of several resolutions comes newest resolved first")
  void theHistoryComesNewestResolvedFirst() throws Exception {
    SkillProposal older = propose("Older resolution", NOW);
    SkillProposal newer = propose("Newer resolution", NOW);
    older.reject(coordinator, "No", NOW.plusSeconds(10));
    newer.reject(coordinator, "No", NOW.plusSeconds(20));
    proposals.saveAll(List.of(older, newer));

    String history = body(list(coordinator, tenant, "?status=REJECTED").andExpect(status().isOk()));

    assertThat(JsonPath.<List<String>>read(history, "$.items[*].name"))
        .containsExactly("Newer resolution", "Older resolution");
  }

  @Test
  @DisplayName("the queue is answered in pages")
  void theQueueIsAnsweredInPages() throws Exception {
    propose("A tool", NOW);
    propose("B tool", NOW.plusSeconds(1));
    propose("C tool", NOW.plusSeconds(2));

    String page = body(list(coordinator, tenant, "?page=1&size=2").andExpect(status().isOk()));

    assertThat(JsonPath.<Integer>read(page, "$.totalElements")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(page, "$.totalPages")).isEqualTo(2);
    assertThat(JsonPath.<List<String>>read(page, "$.items[*].name")).containsExactly("C tool");
  }

  @Test
  @DisplayName("another university's proposals are not in the queue")
  void anotherUniversitysProposalsAreNotInTheQueue() throws Exception {
    propose("Mine", NOW);
    String other = "T" + UUID.randomUUID().toString().substring(0, 8);
    proposals.save(SkillProposal.propose(UUID.randomUUID(), other, student, category, "Theirs", null, NOW));
    when(identity.requireUser(coordinator))
        .thenReturn(new UserView(coordinator, other, UserRole.COORDINATOR, "x@x.pe", null, "Other", null, null, null));

    String queue = body(list(coordinator, other, "").andExpect(status().isOk()));

    assertThat(JsonPath.<List<String>>read(queue, "$.items[*].name")).containsExactly("Theirs");
  }

  @Test
  @DisplayName("the detail lists the catalogue items the proposal looks like")
  void theDetailListsTheSimilarItems() throws Exception {
    String word = "qz" + UUID.randomUUID().toString().replaceAll("[^a-f]", "").substring(0, 6);
    CatalogItem existing = tool(word + ".js");
    SkillProposal proposal = propose(word + "JS Advanced", NOW);

    String detail =
        body(
            mockMvc
                .perform(
                    get("/api/v1/coordinator/skill-proposals/" + proposal.getId())
                        .headers(headers(coordinator, tenant)))
                .andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(detail, "$.proposal.id")).isEqualTo(proposal.getId().toString());
    assertThat(JsonPath.<String>read(detail, "$.proposal.proposer.fullName")).isEqualTo("Ana Torres");
    assertThat(JsonPath.<List<String>>read(detail, "$.similar[*].id")).containsExactly(existing.getId().toString());
  }

  @Test
  @DisplayName("a proposal of another university is not found, and neither is one that does not exist")
  void aProposalOfAnotherUniversityIsNotFound() throws Exception {
    SkillProposal mine = propose("Mine", NOW);
    String other = "T" + UUID.randomUUID().toString().substring(0, 8);

    mockMvc
        .perform(get("/api/v1/coordinator/skill-proposals/" + mine.getId()).headers(headers(coordinator, other)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v1/coordinator/skill-proposals/" + UUID.randomUUID()).headers(headers(coordinator, tenant)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("a student cannot read the queue, the history or a proposal")
  void aStudentCannotReadTheQueue() throws Exception {
    SkillProposal proposal = propose("Mine", NOW);

    list(student, tenant, "").andExpect(status().isForbidden());
    list(student, tenant, "?status=REJECTED").andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/coordinator/skill-proposals/" + proposal.getId()).headers(headers(student, tenant)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("a status that does not exist, a page below zero or no user are refused")
  void aBadRequestIsRefused() throws Exception {
    list(coordinator, tenant, "?status=MAYBE").andExpect(status().isBadRequest());
    list(coordinator, tenant, "?page=-1").andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/coordinator/skill-proposals").header("X-Tenant-Id", tenant))
        .andExpect(status().isBadRequest());
  }
}
