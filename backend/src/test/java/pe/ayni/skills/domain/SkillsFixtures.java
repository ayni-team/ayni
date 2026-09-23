package pe.ayni.skills.domain;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;

/** The bits of the catalogue the tests in this package keep needing. */
final class SkillsFixtures {

  static final String UPC = "UPC";
  static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

  private SkillsFixtures() {}

  static Category category(String name) {
    return new Category(UUID.randomUUID(), name, (short) 0);
  }

  /** A global tool: same for every university, no course code. */
  static CatalogItem globalItem(UUID categoryId, String name) {
    return new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, categoryId, name, null,
        null, NOW);
  }

  /** A course that belongs to {@link #UPC}. */
  static CatalogItem universityItem(UUID categoryId, String name, String courseCode) {
    return new CatalogItem(
        UUID.randomUUID(), CatalogScope.UNIVERSITY, UPC, categoryId, name, null, courseCode, NOW);
  }
}
