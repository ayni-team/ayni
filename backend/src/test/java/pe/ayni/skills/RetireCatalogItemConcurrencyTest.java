package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.application.OfferApprovedCourseUseCase;
import pe.ayni.skills.application.RetireCatalogItemUseCase;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * The two races of retiring an item.
 *
 * <p>A tutor offering a course while a coordinator retires it: without the shared lock the tutor
 * reads the item as active, the retirement commits, and the tutor's offer is enabled on an item that
 * is no longer in the catalogue. Whichever comes first must win and the other must be refused. Make
 * {@code OfferApprovedCourseUseCase} read the item without {@code lockByIdAndTenantVisibilityForShare}
 * and the first test fails.
 *
 * <p>Two coordinators retiring the same item at once: without the lock on the item both read it as
 * active and both retire it. Remove {@code @Lock} from {@code
 * CatalogItemRepository.lockByIdAndTenantVisibility} and the second test fails.
 *
 * <p>Repeated over several rounds because a race is a matter of timing, with an item of its own in
 * each.
 */
@SpringBootTest
class RetireCatalogItemConcurrencyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final int ROUNDS = 25;

  @Autowired private RetireCatalogItemUseCase retire;
  @Autowired private OfferApprovedCourseUseCase offer;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private CategoryRepository categories;
  @Autowired private OfferedSkillRepository offeredSkills;
  @MockitoBean private IdentityApi identity;

  private final UUID coordinator = UUID.randomUUID();
  private final UUID otherCoordinator = UUID.randomUUID();

  @BeforeEach
  void coordinatorsAndAThresholdEveryoneClears() {
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call ->
                new UserView(call.getArgument(0), UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", "America/Lima", new BigDecimal("13.00"), true));
  }

  @Test
  @DisplayName("A tutor offering a course while it is retired: one of them wins and the other is refused")
  void aTutorOfferingWhileTheItemIsRetired() throws Exception {

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        CatalogItem course = course();
        UUID tutor = UUID.randomUUID();
        when(identity.approvedCourses(tutor))
            .thenReturn(List.of(new ApprovedCourseView(course.getCourseCode(), course.getName(), new BigDecimal("16.00"), "2026-1")));

        CyclicBarrier bothReady = new CyclicBarrier(2);
        Future<Boolean> offering =
            pool.submit(
                attempt(bothReady, () -> offer.execute(tutor, course.getId()), SkillsRuleViolation.class));
        Future<Boolean> retiring =
            pool.submit(
                attempt(
                    bothReady,
                    () -> retire.execute(coordinator, course.getId(), 0),
                    SkillsStateConflict.class));

        List<Boolean> outcomes = List.of(offering.get(30, TimeUnit.SECONDS), retiring.get(30, TimeUnit.SECONDS));
        assertThat(outcomes)
            .as("round %d: the tutor is enabled, or the item is retired, never both", round)
            .containsExactlyInAnyOrder(true, false);

        // Whoever won, nobody is left offering a retired item.
        boolean retired = !catalogItems.findById(course.getId()).orElseThrow().isActive();
        boolean enabled =
            offeredSkills
                .findByTenantIdAndTutorIdAndCatalogItemId(UPC, tutor, course.getId())
                .filter(skill -> skill.getStatus() == OfferedSkillStatus.ENABLED)
                .isPresent();
        assertThat(retired && enabled).as("round %d: an enabled offer on a retired item", round).isFalse();
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("Two coordinators retiring the same item at once retire it once")
  void twoCoordinatorsRetiringTheSameItem() throws Exception {

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        CatalogItem tool = tool();

        CyclicBarrier bothReady = new CyclicBarrier(2);
        Future<Boolean> first =
            pool.submit(
                attempt(bothReady, () -> retire.execute(coordinator, tool.getId(), 0), SkillsStateConflict.class));
        Future<Boolean> second =
            pool.submit(
                attempt(
                    bothReady, () -> retire.execute(otherCoordinator, tool.getId(), 0), SkillsStateConflict.class));

        assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
            .as("outcomes of round %d: one retires it, the other is told it is retired", round)
            .containsExactlyInAnyOrder(true, false);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  /** Runs the work once both threads are ready; {@code true} if it went through, {@code false} if refused. */
  private Callable<Boolean> attempt(CyclicBarrier bothReady, Runnable work, Class<? extends RuntimeException> refusal) {
    return () -> {
      bothReady.await();
      try {
        TenantContext.runAs(UPC, work);
        return true;
      } catch (RuntimeException refused) {
        if (refusal.isInstance(refused)) {
          return false;
        }
        throw refused;
      }
    };
  }

  private UUID category() {
    return categories.save(new Category(UUID.randomUUID(), "Category " + UUID.randomUUID(), (short) 0)).getId();
  }

  private CatalogItem course() {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(),
            CatalogScope.UNIVERSITY,
            UPC,
            category(),
            "Course " + UUID.randomUUID(),
            null,
            "C" + UUID.randomUUID().toString().substring(0, 8),
            Instant.now()));
  }

  private CatalogItem tool() {
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.GLOBAL, null, category(), "Tool " + UUID.randomUUID(), null, null, Instant.now()));
  }
}
