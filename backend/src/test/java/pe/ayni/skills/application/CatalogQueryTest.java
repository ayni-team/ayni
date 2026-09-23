package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;

class CatalogQueryTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final CatalogQuery query = new CatalogQuery(catalogItems);

  @Test
  @DisplayName("returns the active items visible to the current tenant")
  void returnsTheActiveItemsVisibleToTheCurrentTenant() {
    CatalogItem python =
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, UUID.randomUUID(), "Python", null, null,
            NOW);
    when(catalogItems.findByTenantVisibilityAndStatus(UPC, CatalogItemStatus.ACTIVE))
        .thenReturn(List.of(python));

    List<CatalogItem> visible = runAsAndGet(UPC, query::visibleItems);

    assertThat(visible).containsExactly(python);
  }

  private static <T> T runAsAndGet(String tenantId, java.util.function.Supplier<T> work) {
    Object[] result = new Object[1];
    TenantContext.runAs(tenantId, () -> result[0] = work.get());
    @SuppressWarnings("unchecked")
    T typed = (T) result[0];
    return typed;
  }
}
