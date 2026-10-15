package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * The lookups by identifier of skills always name the university (US16 follow-up), against a real
 * PostgreSQL.
 *
 * <p>The repositories are called from the test, so they get the owner's connection and row level
 * security does not apply (see {@code FixtureOwnerTestDataSource}): what is proven is the query's
 * own filter, the one the application would rely on if a policy were ever missing. The plain {@code
 * findAllById} these lookups replaced answers every row asked for, whatever its university.
 *
 * <p>Configured like {@code ValidationRequestDatabaseTest} so both share one Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TenantScopedLookupsDatabaseTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");

  private final String upc = "U" + unique();
  private final String utec = "T" + unique();

  @Autowired private CategoryRepository categories;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private OfferedSkillRepository offeredSkills;
  @MockitoBean private IdentityApi identity;

  private static String unique() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  private UUID category() {
    return categories.save(new Category(UUID.randomUUID(), "Category " + unique(), (short) 0)).getId();
  }

  private CatalogItem tool() {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, category(), "Tool " + unique(), null, null, NOW));
  }

  private CatalogItem courseOf(String tenant) {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(),
            CatalogScope.UNIVERSITY,
            tenant,
            category(),
            "Course " + unique(),
            null,
            "C" + unique(),
            NOW));
  }

  @Test
  @DisplayName("Items by identifier are the global ones and the university's own, never another's")
  void itemsByIdentifierAreTheVisibleOnes() {
    CatalogItem figma = tool();
    CatalogItem upcCourse = courseOf(upc);
    CatalogItem utecCourse = courseOf(utec);
    List<UUID> asked = List.of(figma.getId(), upcCourse.getId(), utecCourse.getId());

    assertThat(catalogItems.findAllByIdInAndTenantVisibility(asked, upc))
        .extracting(CatalogItem::getId)
        .containsExactlyInAnyOrder(figma.getId(), upcCourse.getId());
    assertThat(catalogItems.findAllByIdInAndTenantVisibility(asked, utec))
        .extracting(CatalogItem::getId)
        .containsExactlyInAnyOrder(figma.getId(), utecCourse.getId());
    assertThat(catalogItems.findByIdAndTenantVisibility(utecCourse.getId(), upc)).isEmpty();
    assertThat(catalogItems.findByIdAndTenantVisibility(figma.getId(), upc)).isPresent();
  }

  @Test
  @DisplayName("Offers by identifier are the university's own, never another's")
  void offersByIdentifierAreTheUniversitysOwn() {
    CatalogItem figma = tool();
    OfferedSkill mine =
        offeredSkills.save(OfferedSkill.requestValidation(UUID.randomUUID(), upc, UUID.randomUUID(), figma.getId(), NOW));
    OfferedSkill theirs =
        offeredSkills.save(OfferedSkill.requestValidation(UUID.randomUUID(), utec, UUID.randomUUID(), figma.getId(), NOW));

    assertThat(offeredSkills.findByTenantIdAndIdIn(upc, List.of(mine.getId(), theirs.getId())))
        .extracting(OfferedSkill::getId)
        .containsExactly(mine.getId());
  }
}
