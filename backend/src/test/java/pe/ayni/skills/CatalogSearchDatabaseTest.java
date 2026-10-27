package pe.ayni.skills;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;

/**
 * {@code GET /api/v1/catalog} over HTTP against a real PostgreSQL: the filters are optional
 * parameters of one query, and whether PostgreSQL accepts them as {@code null} is only known by
 * running it.
 *
 * <p>Every test works inside a category of its own, so rows left by other tests do not count.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogSearchDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-09-22T15:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @MockitoBean private IdentityApi identity;

  private UUID category;

  @BeforeEach
  void aCategoryOfItsOwn() {
    category =
        categories
            .save(new Category(UUID.randomUUID(), "Search " + UUID.randomUUID(), (short) 0))
            .getId();
  }

  private void item(CatalogScope scope, String tenantId, String name) {
    catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(),
            scope,
            tenantId,
            category,
            name,
            null,
            scope == CatalogScope.GLOBAL ? null : "C" + UUID.randomUUID().toString().substring(0, 8),
            NOW));
  }

  @Test
  void aCategoryShowsTheGlobalItemsAndOnlyTheCurrentUniversitysCourses() throws Exception {
    item(CatalogScope.GLOBAL, null, "Python");
    item(CatalogScope.UNIVERSITY, "UPC", "Databases I");
    item(CatalogScope.UNIVERSITY, "UTEC", "Operating Systems");

    mockMvc
        .perform(get("/api/v1/catalog").header("X-Tenant-Id", "UPC").param("category", category.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.items[0].name").value("Databases I"))
        .andExpect(jsonPath("$.items[1].name").value("Python"));
  }

  @Test
  void theTextNarrowsByPartOfTheNameIgnoringCase() throws Exception {
    item(CatalogScope.GLOBAL, null, "Python");
    item(CatalogScope.GLOBAL, null, "Figma");

    mockMvc
        .perform(
            get("/api/v1/catalog")
                .header("X-Tenant-Id", "UPC")
                .param("category", category.toString())
                .param("q", "PYTH"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Python"));
  }

  @Test
  void theCatalogueIsAnsweredInPages() throws Exception {
    item(CatalogScope.GLOBAL, null, "A tool");
    item(CatalogScope.GLOBAL, null, "B tool");
    item(CatalogScope.GLOBAL, null, "C tool");

    mockMvc
        .perform(
            get("/api/v1/catalog")
                .header("X-Tenant-Id", "UPC")
                .param("category", category.toString())
                .param("page", "1")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("C tool"));
  }

  @Test
  void withoutFiltersTheWholeVisibleCatalogueIsAnswered() throws Exception {
    mockMvc
        .perform(get("/api/v1/catalog").header("X-Tenant-Id", "UPC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));
  }
}
