package pe.ayni.skills;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;

/**
 * {@code GET /api/v1/catalog/similar} over HTTP against a real PostgreSQL: which items the
 * university can see, and that a retired one is not among them.
 *
 * <p>The comparison reads the whole visible catalogue, so every test writes names built from random
 * letters that no other test uses.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SimilarItemsDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @MockitoBean private IdentityApi identity;

  private UUID category;
  private String word;

  @BeforeEach
  void aWordOfItsOwn() {
    category =
        categories.save(new Category(UUID.randomUUID(), "Similar " + UUID.randomUUID(), (short) 0)).getId();
    StringBuilder letters = new StringBuilder("qz");
    for (int i = 0; i < 10; i++) {
      letters.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
    }
    word = letters.toString();
  }

  private CatalogItem save(CatalogScope scope, String tenantId, String name, CatalogItemStatus status) {
    CatalogItem item =
        new CatalogItem(
            UUID.randomUUID(),
            scope,
            tenantId,
            category,
            name,
            null,
            scope == CatalogScope.GLOBAL ? null : "C" + UUID.randomUUID().toString().substring(0, 8),
            NOW);
    if (status == CatalogItemStatus.RETIRED) {
      item.retire();
    }
    return catalogItems.save(item);
  }

  @Test
  @DisplayName("a global tool and a course of the university are shown, written another way")
  void aGlobalToolAndACourseOfTheUniversityAreShown() throws Exception {
    save(CatalogScope.GLOBAL, null, word.toUpperCase() + ".js", CatalogItemStatus.ACTIVE);
    save(CatalogScope.UNIVERSITY, "UPC", word + " avanzado", CatalogItemStatus.ACTIVE);

    mockMvc
        .perform(get("/api/v1/catalog/similar").header("X-Tenant-Id", "UPC").param("name", word + "JS"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("a course of another university is not shown")
  void aCourseOfAnotherUniversityIsNotShown() throws Exception {
    save(CatalogScope.UNIVERSITY, "UTEC", word, CatalogItemStatus.ACTIVE);

    mockMvc
        .perform(get("/api/v1/catalog/similar").header("X-Tenant-Id", "UPC").param("name", word))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("a retired item is not shown")
  void aRetiredItemIsNotShown() throws Exception {
    save(CatalogScope.GLOBAL, null, word, CatalogItemStatus.RETIRED);

    mockMvc
        .perform(get("/api/v1/catalog/similar").header("X-Tenant-Id", "UPC").param("name", word))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("an item is answered with what the student needs to recognise it")
  void anItemIsAnsweredWithWhatTheStudentNeedsToRecogniseIt() throws Exception {
    CatalogItem tool = save(CatalogScope.GLOBAL, null, word, CatalogItemStatus.ACTIVE);

    mockMvc
        .perform(get("/api/v1/catalog/similar").header("X-Tenant-Id", "UPC").param("name", word))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(tool.getId().toString()))
        .andExpect(jsonPath("$[0].scope").value("GLOBAL"))
        .andExpect(jsonPath("$[0].name").value(word));
  }

  @Test
  @DisplayName("a missing, blank or oversized name is refused")
  void aMissingBlankOrOversizedNameIsRefused() throws Exception {
    mockMvc
        .perform(get("/api/v1/catalog/similar").header("X-Tenant-Id", "UPC"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/catalog/similar").header("X-Tenant-Id", "UPC").param("name", "  "))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/catalog/similar").header("X-Tenant-Id", "UPC").param("name", "x".repeat(161)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("a request without a university is refused")
  void aRequestWithoutAUniversityIsRefused() throws Exception {
    mockMvc
        .perform(get("/api/v1/catalog/similar").param("name", word))
        .andExpect(status().isBadRequest());
  }
}
