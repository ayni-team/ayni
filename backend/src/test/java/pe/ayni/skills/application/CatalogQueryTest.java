package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
  @DisplayName("returns the active items visible to the current tenant, sorted by name")
  void returnsTheActiveItemsVisibleToTheCurrentTenant() {
    CatalogItem python =
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, UUID.randomUUID(), "Python", null, null,
            NOW);
    when(catalogItems.searchVisible(
            eq(UPC), eq(CatalogItemStatus.ACTIVE), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(python)));

    Page<CatalogItem> visible = runAsAndGet(UPC, () -> query.visibleItems(null, " ", 0, 20));

    assertThat(visible.getContent()).containsExactly(python);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(catalogItems)
        .searchVisible(eq(UPC), eq(CatalogItemStatus.ACTIVE), isNull(), isNull(), pageable.capture());
    assertThat(pageable.getValue().getSort().getOrderFor("name")).isNotNull();
  }

  @Test
  @DisplayName("the text is matched literally, ignoring case, with its wildcards escaped")
  void theTextIsMatchedLiterally() {
    UUID category = UUID.randomUUID();
    when(catalogItems.searchVisible(any(), any(), any(), any(), any(Pageable.class)))
        .thenReturn(Page.empty());

    runAsAndGet(UPC, () -> query.visibleItems(category, " C_100% ", 1, 10));

    verify(catalogItems)
        .searchVisible(
            eq(UPC),
            eq(CatalogItemStatus.ACTIVE),
            eq(category),
            eq("%c\\_100\\%%"),
            any(Pageable.class));
  }

  private static <T> T runAsAndGet(String tenantId, java.util.function.Supplier<T> work) {
    Object[] result = new Object[1];
    TenantContext.runAs(tenantId, () -> result[0] = work.get());
    @SuppressWarnings("unchecked")
    T typed = (T) result[0];
    return typed;
  }
}
