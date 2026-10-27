package pe.ayni.skills.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.OfferedSkill;

/** The bits of the catalogue the tests in this package keep needing. */
final class SkillsFixtures {

  static final String UPC = "UPC";
  static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
  static final UUID TUTOR = UUID.fromString("11111111-1111-4111-8111-111111111111");

  /** The minimum teaching grade UPC uses in these tests. */
  static final BigDecimal THRESHOLD = new BigDecimal("13.00");

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

  static OfferedSkill enabledSkill(UUID catalogItemId, BigDecimal grade) {
    return OfferedSkill.enableByAcademicRecord(
        UUID.randomUUID(), UPC, TUTOR, catalogItemId, grade, THRESHOLD, NOW);
  }
}
