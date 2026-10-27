package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/**
 * The categories of US42, over HTTP against a real database, signed in with real session tokens
 * (TS03). One test per scenario of {@code features/US42-propose-a-skill.feature} about them, with
 * the same names.
 *
 * <p>Nothing is mocked. The categories are global and this database is shared by every test of the
 * module, so each test adds categories with names of its own and checks where they stand among the
 * rest, never the whole list.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class ProposalCategoriesAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @Autowired private CategoryRepository categories;
  @Autowired private SkillProposalRepository proposals;

  private SignedInPeople people;
  private String tenant;
  private Person student;
  private String word;

  @BeforeEach
  void aStudentOfTheirOwnUniversity() {
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("K");
    student = people.aStudent(tenant);
    StringBuilder letters = new StringBuilder("qk");
    for (int i = 0; i < 10; i++) {
      letters.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
    }
    word = letters.toString();
  }

  private MockHttpServletRequestBuilder as(Person person, MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer " + person.token());
  }

  private String listed(Person person) throws Exception {
    return mockMvc
        .perform(as(person, get("/api/v1/catalog/categories")))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @DisplayName("A student lists the categories to propose a tool in")
  void aStudentListsTheCategoriesToProposeAToolIn() throws Exception {
    UUID last = categories.save(new Category(UUID.randomUUID(), "Zeta " + word, (short) 90)).getId();
    UUID beforeLast =
        categories.save(new Category(UUID.randomUUID(), "Alpha " + word, (short) 90)).getId();
    UUID first = categories.save(new Category(UUID.randomUUID(), "Middle " + word, (short) -90)).getId();

    String read = listed(student);

    // The three that ship with Ayni, by their order, and the ones of this test where their order
    // and then their name put them.
    List<String> ids = JsonPath.read(read, "$[*].id");
    List<String> names = JsonPath.read(read, "$[*].name");
    assertThat(ids.indexOf(first.toString())).isLessThan(names.indexOf("Technology"));
    assertThat(names.indexOf("Technology"))
        .isLessThan(names.indexOf("Languages"))
        .isLessThan(names.indexOf("Design"));
    assertThat(names.indexOf("Design")).isLessThan(ids.indexOf(beforeLast.toString()));
    assertThat(ids.indexOf(beforeLast.toString())).isLessThan(ids.indexOf(last.toString()));
    assertThat(JsonPath.<List<String>>read(read, "$[?(@.id == '" + beforeLast + "')].name"))
        .containsExactly("Alpha " + word);
    String technology =
        JsonPath.<List<String>>read(read, "$[?(@.name == 'Technology')].id").getFirst();
    assertThat(technology).isEqualTo("a0000000-0000-4000-8000-000000000001");

    // The id listed is the one a proposal takes, and the proposal is created in that category.
    String created =
        mockMvc
            .perform(
                as(student, post("/api/v1/tutor/skills/proposals"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","categoryId":"%s","description":"A tool","confirmDistinct":true}
                        """
                            .formatted(word, technology)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID proposalId = UUID.fromString(JsonPath.read(created, "$.id"));
    assertThat(proposals.findByTenantIdAndId(tenant, proposalId))
        .hasValueSatisfying(
            proposal -> {
              assertThat(proposal.getCategoryId()).isEqualTo(UUID.fromString(technology));
              assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
            });
    // And it is the one the catalogue is narrowed by.
    mockMvc
        .perform(as(student, get("/api/v1/catalog").param("category", technology)))
        .andExpect(status().isOk());

    // Without a session nobody is answered.
    mockMvc.perform(get("/api/v1/catalog/categories")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("The category of the courses of a university is not offered for a proposal")
  void theCategoryOfTheCoursesOfAUniversityIsNotOfferedForAProposal() throws Exception {
    Person coordinator = people.aCoordinator(tenant);
    mockMvc
        .perform(
            as(coordinator, post("/api/v1/coordinator/academic-catalog/courses"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"courses":[{"code":"%s","name":"Databases %s"}]}
                    """
                        .formatted(word.substring(0, 8).toUpperCase(), word)))
        .andExpect(status().isOk());
    UUID courses =
        categories.findByName(Category.UNIVERSITY_COURSES).map(Category::getId).orElseThrow();

    String read = listed(student);

    assertThat(JsonPath.<List<String>>read(read, "$[*].id")).doesNotContain(courses.toString());
    assertThat(JsonPath.<List<String>>read(read, "$[*].name"))
        .doesNotContain(Category.UNIVERSITY_COURSES)
        .contains("Technology", "Languages", "Design");
    // The courses are still found in the catalogue under it.
    String catalogue =
        mockMvc
            .perform(as(student, get("/api/v1/catalog").param("category", courses.toString())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<List<String>>read(catalogue, "$.items[*].name")).contains("Databases " + word);
  }
}
