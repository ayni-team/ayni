package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.events.ValidationResolved;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.application.ResolveValidationUseCase;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.domain.model.ValidationStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.ValidationRequestRepository;

/**
 * Two coordinators deciding the same submission at once, one approving and the other rejecting.
 *
 * <p>Without the lock on the request both read it as waiting, both decide it and the student is
 * told twice, maybe two opposite things, while the skill ends in whichever write came last. With
 * it the second waits for the first to commit and then finds the submission already decided.
 * Remove {@code @Lock} from {@code ValidationRequestRepository.lockByTenantIdAndId} and this test
 * fails.
 *
 * <p>Repeated over several rounds because a race is a matter of timing, with a submission of its
 * own in each.
 */
@SpringBootTest
class ResolveValidationConcurrencyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private static final int ROUNDS = 10;

  @Autowired private ResolveValidationUseCase resolveValidation;
  @Autowired private OfferedSkillRepository offeredSkills;
  @Autowired private ValidationRequestRepository requests;
  @Autowired private CatalogItemRepository catalogItems;
  @Autowired private CategoryRepository categories;
  @Autowired private ResolvedEvents resolvedEvents;
  @MockitoBean private IdentityApi identity;

  /** Every {@code ValidationResolved} the application published. */
  static class ResolvedEvents {

    private final List<ValidationResolved> published = new CopyOnWriteArrayList<>();

    @EventListener
    void on(ValidationResolved event) {
      published.add(event);
    }

    long about(UUID requestId) {
      return published.stream().filter(event -> event.validationRequestId().equals(requestId)).count();
    }
  }

  @TestConfiguration
  static class Events {

    @Bean
    ResolvedEvents resolvedEvents() {
      return new ResolvedEvents();
    }
  }

  @BeforeEach
  void everyoneIsACoordinator() {
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call ->
                new UserView(
                    call.getArgument(0), UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
  }

  @Test
  @DisplayName("Two coordinators deciding the same submission at once decide it once")
  void twoCoordinatorsAtOnceDecideItOnce() throws Exception {

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        OfferedSkill skill = pendingSkill();
        ValidationRequest request =
            requests.save(ValidationRequest.submit(UUID.randomUUID(), UPC, skill.getId(), null, Instant.now()));

        CyclicBarrier bothReady = new CyclicBarrier(2);
        Callable<Boolean> approving = attempt(bothReady, request, true, null);
        Callable<Boolean> rejecting = attempt(bothReady, request, false, "Not enough evidence");

        Future<Boolean> first = pool.submit(approving);
        Future<Boolean> second = pool.submit(rejecting);

        assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
            .as("outcomes of round %d: one decides it, the other is refused", round)
            .containsExactlyInAnyOrder(true, false);
        assertThat(resolvedEvents.about(request.getId()))
            .as("ValidationResolved events of round %d", round)
            .isEqualTo(1);

        // Whoever won, the skill agrees with the request.
        ValidationRequest decided = requests.findByTenantIdAndId(UPC, request.getId()).orElseThrow();
        OfferedSkill after = offeredSkills.findById(skill.getId()).orElseThrow();
        assertThat(after.getStatus())
            .as("skill of round %d", round)
            .isEqualTo(
                decided.getStatus() == ValidationStatus.APPROVED
                    ? OfferedSkillStatus.ENABLED
                    : OfferedSkillStatus.REJECTED);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private Callable<Boolean> attempt(
      CyclicBarrier bothReady, ValidationRequest request, boolean approved, String reason) {
    UUID coordinator = UUID.randomUUID();
    return () -> {
      bothReady.await();
      try {
        TenantContext.runAs(
            UPC, () -> resolveValidation.execute(coordinator, request.getId(), approved, reason));
        return true;
      } catch (SkillsStateConflict alreadyDecided) {
        return false;
      }
    };
  }

  private OfferedSkill pendingSkill() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UUID category =
        categories.save(new Category(UUID.randomUUID(), "Category " + suffix, (short) 0)).getId();
    CatalogItem tool =
        catalogItems.save(
            new CatalogItem(
                UUID.randomUUID(), CatalogScope.GLOBAL, null, category, "Tool " + suffix, null, null, Instant.now()));
    return offeredSkills.save(
        OfferedSkill.requestValidation(UUID.randomUUID(), UPC, UUID.randomUUID(), tool.getId(), Instant.now()));
  }
}
