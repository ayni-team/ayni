package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.application.WithdrawSkillUseCase;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * Two requests withdrawing the same skill at once.
 *
 * <p>Without the lock on the row both read it as enabled, both withdraw it and both announce it,
 * and the search projection is told twice. With it, the second waits for the first to commit and
 * then finds the skill already withdrawn. Remove {@code @Lock} from {@code
 * OfferedSkillRepository.lockByTenantIdAndId} and this test fails.
 *
 * <p>Repeated over several rounds because a race is a matter of timing: one lucky round proves
 * nothing, and each round uses a skill of its own.
 */
@SpringBootTest
class WithdrawSkillConcurrencyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final int ROUNDS = 10;

  @Autowired private WithdrawSkillUseCase withdrawSkill;
  @Autowired private OfferedSkillRepository offeredSkills;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private CategoryRepository categories;
  @Autowired private WithdrawnEvents withdrawnEvents;

  /** Every {@code SkillWithdrawn} the application published, in the thread that published it. */
  static class WithdrawnEvents {

    private final List<SkillWithdrawn> published = new CopyOnWriteArrayList<>();

    @EventListener
    void on(SkillWithdrawn event) {
      published.add(event);
    }

    long about(CatalogItem course) {
      return published.stream().filter(event -> event.catalogItemId().equals(course.getId())).count();
    }
  }

  @TestConfiguration
  static class Events {

    @Bean
    WithdrawnEvents withdrawnEvents() {
      return new WithdrawnEvents();
    }
  }

  @Test
  @DisplayName("Two requests withdrawing the same skill at once announce it once")
  void twoRequestsAtOnceAnnounceItOnce() throws Exception {

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        UUID tutor = UUID.randomUUID();
        CatalogItem course = seedCourse();
        OfferedSkill skill =
            offeredSkills.save(
                OfferedSkill.enableByAcademicRecord(
                    UUID.randomUUID(),
                    UPC,
                    tutor,
                    course.getId(),
                    new BigDecimal("15.00"),
                    new BigDecimal("13.00"),
                    Instant.now()));

        CyclicBarrier bothReady = new CyclicBarrier(2);
        Callable<Boolean> attempt =
            () -> {
              bothReady.await();
              try {
                TenantContext.runAs(UPC, () -> withdrawSkill.execute(tutor, skill.getId()));
                return true;
              } catch (SkillsStateConflict alreadyWithdrawn) {
                return false;
              }
            };

        Future<Boolean> first = pool.submit(attempt);
        Future<Boolean> second = pool.submit(attempt);

        assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
            .as("outcomes of round %d: one request withdraws it, the other is refused", round)
            .containsExactlyInAnyOrder(true, false);
        assertThat(withdrawnEvents.about(course))
            .as("SkillWithdrawn events of round %d", round)
            .isEqualTo(1);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private CatalogItem seedCourse() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UUID category =
        categories.save(new Category(UUID.randomUUID(), "Category " + suffix, (short) 0)).getId();
    return catalogItems.save(
        new CatalogItem(
            UUID.randomUUID(),
            CatalogScope.UNIVERSITY,
            UPC,
            category,
            "Course " + suffix,
            null,
            "C-" + suffix,
            Instant.now()));
  }
}
