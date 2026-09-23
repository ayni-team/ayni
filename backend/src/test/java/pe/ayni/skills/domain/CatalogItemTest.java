package pe.ayni.skills.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.skills.domain.SkillsFixtures.NOW;
import static pe.ayni.skills.domain.SkillsFixtures.UPC;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;

/** The rule that keeps a global item from ever belonging to a single university, and back. */
class CatalogItemTest {

  private final UUID categoryId = UUID.randomUUID();

  @Test
  @DisplayName("a global item cannot carry a tenant")
  void globalItemCannotCarryATenant() {
    assertThatThrownBy(
            () ->
                new CatalogItem(
                    UUID.randomUUID(),
                    CatalogScope.GLOBAL,
                    UPC,
                    categoryId,
                    "Python",
                    null,
                    null,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a university item must carry a tenant")
  void universityItemMustCarryATenant() {
    assertThatThrownBy(
            () ->
                new CatalogItem(
                    UUID.randomUUID(),
                    CatalogScope.UNIVERSITY,
                    null,
                    categoryId,
                    "Cálculo I",
                    null,
                    "1MAT0101",
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a university item must carry a course code")
  void universityItemMustCarryACourseCode() {
    assertThatThrownBy(
            () ->
                new CatalogItem(
                    UUID.randomUUID(),
                    CatalogScope.UNIVERSITY,
                    UPC,
                    categoryId,
                    "Cálculo I",
                    null,
                    null,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a global item is visible to every tenant")
  void globalItemIsVisibleToEveryTenant() {
    CatalogItem python =
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, categoryId, "Python", null, null, NOW);

    assertThat(python.isVisibleTo(UPC)).isTrue();
    assertThat(python.isVisibleTo("PUCP")).isTrue();
  }

  @Test
  @DisplayName("a university item is only visible to its own tenant")
  void universityItemIsOnlyVisibleToItsOwnTenant() {
    CatalogItem calculus =
        new CatalogItem(
            UUID.randomUUID(),
            CatalogScope.UNIVERSITY,
            UPC,
            categoryId,
            "Cálculo I",
            null,
            "1MAT0101",
            NOW);

    assertThat(calculus.isVisibleTo(UPC)).isTrue();
    assertThat(calculus.isVisibleTo("PUCP")).isFalse();
  }

  @Test
  @DisplayName("retiring an item takes it out of active")
  void retiringAnItemTakesItOutOfActive() {
    CatalogItem python =
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, categoryId, "Python", null, null, NOW);

    assertThat(python.isActive()).isTrue();

    python.retire();

    assertThat(python.isActive()).isFalse();
  }
}
