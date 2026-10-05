package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
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
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/**
 * US42, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US42-propose-a-skill.feature}, with
 * the same names. The scenario tagged {@code @pending} there has no test here, and says why.
 *
 * <p>The comparison with the catalogue reads every item the university can see, so each test builds
 * its names from random letters that no other test uses and cannot be stopped by what another left.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SkillProposalAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final String UTEC = "UTEC";

  /** A student of their own, so that one scenario cannot see another one's proposals. */
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private SkillProposalRepository proposals;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;

  private UUID category;
  private String word;

  /** Background: a category, and a word that belongs to this scenario alone. */
  @BeforeEach
  void aCategoryAndAWordOfItsOwn() {
    category =
        categories.save(new Category(UUID.randomUUID(), "Category " + UUID.randomUUID(), (short) 0)).getId();
    StringBuilder letters = new StringBuilder("qz");
    for (int i = 0; i < 10; i++) {
      letters.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
    }
    word = letters.toString();
  }

  @Test
  @DisplayName("Proposal sent")
  void proposalSent() throws Exception {

    String body =
        propose(student, word, category, "Interface design tool", false)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    SkillProposal stored =
        proposals.findByTenantIdAndId(UPC, UUID.fromString(read(body, "$.id", String.class))).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
    assertThat(stored.getName()).isEqualTo(word);
    assertThat(stored.getDescription()).isEqualTo("Interface design tool");
    assertThat(stored.getCategoryId()).isEqualTo(category);
    assertThat(stored.getProposedBy()).isEqualTo(student);
    assertThat(stored.getResolvedBy()).isNull();
    assertThat(read(body, "$.status", String.class)).isEqualTo("PROPOSED");
  }

  @Test
  @DisplayName("Skill that already exists")
  void skillThatAlreadyExists() throws Exception {

    CatalogItem nodeJs = seedTool(word + ".js");
    String proposed = word + "JS Advanced";

    String refusal =
        propose(student, proposed, category, null, false)
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(read(refusal, "$.similar[*].id", List.class)).containsExactly(nodeJs.getId().toString());
    assertThat(read(refusal, "$.similar[0].name", String.class)).isEqualTo(nodeJs.getName());
    assertThat(read(refusal, "$.message", String.class)).contains(nodeJs.getName());
    assertThat(proposalsOf(student)).isEqualTo("[]");

    String similar =
        mockMvc
            .perform(get("/api/v1/catalog/similar").headers(headers(student, UPC)).param("name", proposed))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(read(similar, "$[*].id", List.class)).containsExactly(nodeJs.getId().toString());

    propose(student, proposed, category, null, true).andExpect(status().isCreated());

    assertThat(read(proposalsOf(student), "$[*].name", List.class)).containsExactly(proposed);
  }

  @Test
  @DisplayName("Following my proposal")
  void followingMyProposal() throws Exception {

    String id = proposedId(student, word);

    String waiting = proposalsOf(student);

    assertThat(read(waiting, "$.length()", Integer.class)).isEqualTo(1);
    assertThat(read(waiting, "$[0].id", String.class)).isEqualTo(id);
    assertThat(read(waiting, "$[0].status", String.class)).isEqualTo("PROPOSED");
    assertThat(read(waiting, "$[0].categoryId", String.class)).isEqualTo(category.toString());
    assertThat(read(waiting, "$[0].categoryName", String.class)).startsWith("Category ");
    assertThat(read(waiting, "$[0].createdAt", String.class)).isNotBlank();
    assertThat(JsonPath.<Object>read(waiting, "$[0].decisionReason")).isNull();
    assertThat(JsonPath.<Object>read(waiting, "$[0].resolvedAt")).isNull();

    reject(id, "It is already covered by another skill");

    String decided = proposalsOf(student);
    assertThat(read(decided, "$[0].status", String.class)).isEqualTo("REJECTED");
    assertThat(read(decided, "$[0].decisionReason", String.class))
        .isEqualTo("It is already covered by another skill");
    assertThat(read(decided, "$[0].resolvedAt", String.class)).isNotBlank();
    assertThat(decided).doesNotContain("resolvedBy");
  }

  @Test
  @DisplayName("No free text in the offer")
  void noFreeTextInTheOffer() throws Exception {

    mockMvc
        .perform(
            post("/api/v1/tutor/skills")
                .headers(headers(student, UPC))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + word + "\"}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/tutor/skills")
                .headers(headers(student, UPC))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogItemId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isNotFound());

    String skills =
        mockMvc
            .perform(get("/api/v1/tutor/skills").headers(headers(student, UPC)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(read(skills, "$", List.class)).isEmpty();
  }

  @Test
  @DisplayName("A name the catalogue already has is refused")
  void aNameTheCatalogueAlreadyHasIsRefused() throws Exception {

    seedTool(word + ".js");

    propose(student, word.toUpperCase() + "JS", category, null, true).andExpect(status().isConflict());

    assertThat(proposalsOf(student)).isEqualTo("[]");
  }

  @Test
  @DisplayName("A proposal already waiting is refused")
  void aProposalAlreadyWaitingIsRefused() throws Exception {

    proposedId(student, word);

    propose(student, word.toUpperCase(), category, null, true).andExpect(status().isConflict());

    assertThat(read(proposalsOf(student), "$.length()", Integer.class)).isEqualTo(1);
  }

  @Test
  @DisplayName("A rejected proposal can be proposed again")
  void aRejectedProposalCanBeProposedAgain() throws Exception {

    String first = proposedId(student, word);
    reject(first, "Not now");

    String second = proposedId(student, word);

    assertThat(second).isNotEqualTo(first);
    String body = proposalsOf(student);
    assertThat(read(body, "$.length()", Integer.class)).isEqualTo(2);
    assertThat(read(body, "$[?(@.id == '" + first + "')].status", List.class)).containsExactly("REJECTED");
    assertThat(read(body, "$[?(@.id == '" + second + "')].status", List.class)).containsExactly("PROPOSED");
  }

  @Test
  @DisplayName("A name or a category that does not fit is refused")
  void aNameOrACategoryThatDoesNotFitIsRefused() throws Exception {

    propose(student, "ab", category, null, false).andExpect(status().isBadRequest());

    propose(student, word, UUID.randomUUID(), null, false).andExpect(status().isNotFound());

    assertThat(proposalsOf(student)).isEqualTo("[]");
  }

  @Test
  @DisplayName("Proposals are private to their author")
  void proposalsArePrivateToTheirAuthor() throws Exception {

    proposedId(student, word);

    assertThat(proposalsOf(UUID.randomUUID())).isEqualTo("[]");
    assertThat(proposalsOf(student, UTEC)).isEqualTo("[]");
  }

  private HttpHeaders headers(UUID user, String tenant) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Tenant-Id", tenant);
    headers.add("X-User-Id", user.toString());
    return headers;
  }

  private ResultActions propose(
      UUID user, String name, UUID categoryId, String description, boolean confirmDistinct)
      throws Exception {
    String descriptionJson = description == null ? "null" : "\"" + description + "\"";
    return mockMvc.perform(
        post("/api/v1/tutor/skills/proposals")
            .headers(headers(user, UPC))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"name":"%s","categoryId":"%s","description":%s,"confirmDistinct":%s}
                """
                    .formatted(name, categoryId, descriptionJson, confirmDistinct)));
  }

  /** Proposes through the endpoint and returns the identifier of the proposal. */
  private String proposedId(UUID user, String name) throws Exception {
    String body =
        propose(user, name, category, null, false)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return read(body, "$.id", String.class);
  }

  private String proposalsOf(UUID user) throws Exception {
    return proposalsOf(user, UPC);
  }

  private String proposalsOf(UUID user, String tenant) throws Exception {
    return mockMvc
        .perform(get("/api/v1/tutor/skills/proposals").headers(headers(user, tenant)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /**
   * What a moderator's rejection leaves in the table. Resolving is US43, which does not exist yet,
   * so the decision is written directly: this test is about the student reading it.
   */
  private void reject(String proposalId, String reason) {
    jdbc.update(
        """
        update skills.skill_proposals
        set status = 'REJECTED', resolved_by = ?, resolved_at = now(), decision_reason = ?
        where id = ?
        """,
        UUID.randomUUID(),
        reason,
        UUID.fromString(proposalId));
  }

  private CatalogItem seedTool(String name) {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, category, name, null, null, Instant.now()));
  }

  private static <T> T read(String json, String path, Class<T> type) {
    return type.cast(JsonPath.read(json, path));
  }
}
