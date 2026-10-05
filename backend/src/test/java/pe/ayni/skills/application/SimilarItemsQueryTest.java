package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;

/** US42, scenario 2: the student sees what already exists before proposing. */
class SimilarItemsQueryTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");
  private static final UUID CATEGORY = UUID.randomUUID();

  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final SimilarItemsQuery query = new SimilarItemsQuery(catalogItems);

  private static CatalogItem tool(String name) {
    return new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, CATEGORY, name, null, null, NOW);
  }

  private List<CatalogItem> similarTo(String name) {
    AtomicReference<List<CatalogItem>> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.similarTo(name)));
    return result.get();
  }

  @Test
  @DisplayName("the catalogue is read for the university of the request, active items only")
  void theCatalogueIsReadForTheUniversityOfTheRequest() {
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of());

    similarTo("Python");

    verify(catalogItems).findVisible(UPC, CatalogItemStatus.ACTIVE);
  }

  @Test
  @DisplayName("an item written another way is shown, and one that is not alike is not")
  void anItemWrittenAnotherWayIsShown() {
    CatalogItem nodeJs = tool("Node.js");
    CatalogItem figma = tool("Figma");
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of(figma, nodeJs));

    assertThat(similarTo("NodeJS")).containsExactly(nodeJs);
  }

  @Test
  @DisplayName("nothing alike answers an empty list")
  void nothingAlikeAnswersAnEmptyList() {
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE))
        .thenReturn(List.of(tool("Figma"), tool("Docker")));

    assertThat(similarTo("Photoshop")).isEmpty();
  }

  @Test
  @DisplayName("the closest comes first, and ties are broken by name")
  void theClosestComesFirst() {
    CatalogItem exact = tool("Python");
    CatalogItem inside = tool("Programming in Python");
    CatalogItem typo = tool("Pythn");
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE))
        .thenReturn(List.of(inside, typo, exact));

    assertThat(similarTo("Python")).containsExactly(exact, inside, typo);
  }

  @Test
  @DisplayName("no more than five are shown")
  void noMoreThanFiveAreShown() {
    List<CatalogItem> many =
        List.of(
            tool("Python 1"), tool("Python 2"), tool("Python 3"), tool("Python 4"), tool("Python 5"),
            tool("Python 6"), tool("Python 7"));
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(many);

    assertThat(similarTo("Python")).hasSize(SimilarItemsQuery.MAX_RESULTS);
  }
}
