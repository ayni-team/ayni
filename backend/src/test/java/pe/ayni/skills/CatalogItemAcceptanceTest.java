package pe.ayni.skills;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;

/**
 * {@code GET /api/v1/catalog/{id}} of W23 over HTTP against a real PostgreSQL, signed in with real
 * session tokens. One test per scenario of {@code features/W23-names-in-responses.feature} that is
 * about the catalogue, with the same name.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class CatalogItemAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;

  private SignedInPeople people;
  private String tenant;
  private Person student;
  private UUID category;

  @BeforeEach
  void aStudentOfAUniversity() {
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("C");
    student = people.aStudent(tenant);
    category =
        categories.save(new Category(UUID.randomUUID(), "Category " + UUID.randomUUID(), (short) 0)).getId();
  }

  private CatalogItem anItem(CatalogScope scope, String tenantId, String name, String courseCode) {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(), scope, tenantId, category, name, "About " + name, courseCode, Instant.now()));
  }

  private ResultActions ask(Person who, UUID id) throws Exception {
    return mockMvc.perform(
        get("/api/v1/catalog/{id}", id).header("Authorization", "Bearer " + who.token()));
  }

  @Test
  @DisplayName("A catalogue item is read by its identifier")
  void aCatalogueItemIsReadByItsIdentifier() throws Exception {
    CatalogItem databases = anItem(CatalogScope.UNIVERSITY, tenant, "Bases de Datos I", "1ASI0616");
    CatalogItem figma = anItem(CatalogScope.GLOBAL, null, "Figma " + UUID.randomUUID(), null);
    CatalogItem retired = anItem(CatalogScope.UNIVERSITY, tenant, "Cálculo Diferencial", "1ACC0001");
    retired.retire();
    catalogItems.save(retired);

    ask(student, databases.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(databases.getId().toString()))
        .andExpect(jsonPath("$.scope").value("UNIVERSITY"))
        .andExpect(jsonPath("$.name").value("Bases de Datos I"))
        .andExpect(jsonPath("$.description").value("About Bases de Datos I"))
        .andExpect(jsonPath("$.courseCode").value("1ASI0616"));
    ask(student, figma.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("GLOBAL"))
        .andExpect(jsonPath("$.name").value(figma.getName()))
        .andExpect(jsonPath("$.courseCode").doesNotExist());
    // The listing no longer shows a retired course, but a past tutoring still has to name it.
    ask(student, retired.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Cálculo Diferencial"))
        .andExpect(jsonPath("$.courseCode").value("1ACC0001"));
  }

  @Test
  @DisplayName("An item of another university is not found")
  void anItemOfAnotherUniversityIsNotFound() throws Exception {
    String other = people.aUniversity("D");
    CatalogItem elsewhere = anItem(CatalogScope.UNIVERSITY, other, "Bases de Datos I", "1ASI0616");

    ask(student, elsewhere.getId())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.path").value("/api/v1/catalog/" + elsewhere.getId()));
    ask(student, UUID.randomUUID()).andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/catalog/{id}", elsewhere.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/v1/catalog/not-a-uuid").header("Authorization", "Bearer " + student.token()))
        .andExpect(status().isBadRequest());
  }
}
