package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.application.MergeCatalogItemsUseCase;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;

/**
 * Two coordinators joining the same two items at once, in opposite directions.
 *
 * <p>Each one must lock both items. If each took them in the order it was given, each would hold
 * one and wait for the other, and the database would end one of them with a deadlock. Locking in the
 * same order whichever way the join goes makes the second wait for the first, and then find that
 * one of the items is already retired. Order the locks by the given direction in
 * {@code MergeCatalogItemsUseCase} and this test fails.
 *
 * <p>Repeated over several rounds because a race is a matter of timing, with a pair of its own in
 * each.
 */
@SpringBootTest
class MergeCatalogItemsConcurrencyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final int ROUNDS = 30;

  @Autowired private MergeCatalogItemsUseCase merge;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private CategoryRepository categories;
  @MockitoBean private IdentityApi identity;

  private final UUID coordinator = UUID.randomUUID();
  private final UUID otherCoordinator = UUID.randomUUID();

  @BeforeEach
  void everyoneIsACoordinator() {
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call ->
                new UserView(call.getArgument(0), UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
  }

  @Test
  @DisplayName("Two coordinators joining the same pair in opposite directions join it once, with no deadlock")
  void oppositeDirectionsJoinItOnce() throws Exception {

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        UUID category =
            categories.save(new Category(UUID.randomUUID(), "Category " + UUID.randomUUID(), (short) 0)).getId();
        CatalogItem first = tool(category);
        CatalogItem second = tool(category);

        CyclicBarrier bothReady = new CyclicBarrier(2);
        Future<Boolean> forward = pool.submit(attempt(bothReady, coordinator, first, second));
        Future<Boolean> backward = pool.submit(attempt(bothReady, otherCoordinator, second, first));

        assertThat(List.of(forward.get(30, TimeUnit.SECONDS), backward.get(30, TimeUnit.SECONDS)))
            .as("outcomes of round %d: one joins them, the other finds one already retired", round)
            .containsExactlyInAnyOrder(true, false);
        long retired =
            List.of(first, second).stream()
                .filter(item -> !catalogItems.findById(item.getId()).orElseThrow().isActive())
                .count();
        assertThat(retired).as("items retired in round %d", round).isEqualTo(1);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private Callable<Boolean> attempt(CyclicBarrier bothReady, UUID asking, CatalogItem removed, CatalogItem kept) {
    return () -> {
      bothReady.await();
      try {
        TenantContext.runAs(UPC, () -> merge.execute(asking, removed.getId(), kept.getId()));
        return true;
      } catch (SkillsRuleViolation refused) {
        // The other one got there first: the duplicate is retired, or the one that would stay is.
        return false;
      }
    };
  }

  private CatalogItem tool(UUID category) {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, category, "Tool " + UUID.randomUUID(), null, null, Instant.now()));
  }
}
