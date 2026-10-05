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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.application.ResolveProposalUseCase;
import pe.ayni.skills.application.ResolveProposalUseCase.Decision;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/**
 * The two races of resolving proposals.
 *
 * <p>Two coordinators on the same proposal, one approving and the other rejecting: without the lock
 * on the proposal both read it as waiting and decide it, the catalogue gets an item the proposal
 * says it did not, and the student is told whichever came last. Remove {@code @Lock} from {@code
 * SkillProposalRepository.lockByTenantIdAndId} and the first test fails.
 *
 * <p>Two universities approving a proposal of the same tool at once: without the lock on the global
 * catalogue both find the name free and the catalogue gets the tool twice. Remove the call to {@code
 * lockGlobalCatalogue} from {@code ResolveProposalUseCase} and the second test fails.
 *
 * <p>Repeated over several rounds because a race is a matter of timing, with names and universities
 * of their own in each.
 */
@SpringBootTest
class ResolveProposalConcurrencyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SkillsTestDatabase.INSTANCE;

  private static final int ROUNDS = 10;

  @Autowired private ResolveProposalUseCase resolveProposal;
  @Autowired private SkillProposalRepository proposals;
  @Autowired private CategoryRepository categories;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;

  @BeforeEach
  void everyoneIsACoordinator() {
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call ->
                new UserView(
                    call.getArgument(0), "UPC", UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
  }

  @Test
  @DisplayName("Two coordinators resolving the same proposal at once resolve it once")
  void twoCoordinatorsAtOnceResolveItOnce() throws Exception {

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        String tenant = tenant();
        String name = uniqueName();
        SkillProposal proposal = proposal(tenant, name);

        CyclicBarrier bothReady = new CyclicBarrier(2);
        Future<Boolean> approving = pool.submit(attempt(bothReady, tenant, proposal, Decision.APPROVE, null));
        Future<Boolean> rejecting = pool.submit(attempt(bothReady, tenant, proposal, Decision.REJECT, "Not now"));

        assertThat(List.of(approving.get(30, TimeUnit.SECONDS), rejecting.get(30, TimeUnit.SECONDS)))
            .as("outcomes of round %d: one resolves it, the other is refused", round)
            .containsExactlyInAnyOrder(true, false);

        // Whoever won, the catalogue agrees with the proposal.
        SkillProposal resolved = proposals.findByTenantIdAndId(tenant, proposal.getId()).orElseThrow();
        assertThat(globalItemsNamed(name))
            .as("items named %s in round %d", name, round)
            .isEqualTo(resolved.getStatus() == ProposalStatus.APPROVED ? 1 : 0);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("Two universities approving the same tool at once add it once")
  void twoUniversitiesApprovingTheSameToolAddItOnce() throws Exception {

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        String name = uniqueName();
        String first = tenant();
        String second = tenant();
        SkillProposal firstProposal = proposal(first, name);
        SkillProposal secondProposal = proposal(second, name);

        CyclicBarrier bothReady = new CyclicBarrier(2);
        Future<Boolean> one = pool.submit(attempt(bothReady, first, firstProposal, Decision.APPROVE, null));
        Future<Boolean> other = pool.submit(attempt(bothReady, second, secondProposal, Decision.APPROVE, null));

        assertThat(List.of(one.get(30, TimeUnit.SECONDS), other.get(30, TimeUnit.SECONDS)))
            .as("outcomes of round %d: one adds the tool, the other is told it exists", round)
            .containsExactlyInAnyOrder(true, false);
        assertThat(globalItemsNamed(name)).as("items named %s in round %d", name, round).isEqualTo(1);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private Callable<Boolean> attempt(
      CyclicBarrier bothReady, String tenant, SkillProposal proposal, Decision decision, String reason) {
    UUID coordinator = UUID.randomUUID();
    return () -> {
      bothReady.await();
      try {
        TenantContext.runAs(
            tenant, () -> resolveProposal.execute(coordinator, proposal.getId(), decision, null, reason));
        return true;
      } catch (SkillsStateConflict refused) {
        return false;
      }
    };
  }

  private SkillProposal proposal(String tenant, String name) {
    UUID category =
        categories.save(new Category(UUID.randomUUID(), "Category " + UUID.randomUUID(), (short) 0)).getId();
    return proposals.save(
        SkillProposal.propose(UUID.randomUUID(), tenant, UUID.randomUUID(), category, name, null, Instant.now()));
  }

  private int globalItemsNamed(String name) {
    return jdbc.queryForObject(
        "select count(*) from skills.catalog_items where scope = 'GLOBAL' and lower(name) = lower(?)",
        Integer.class,
        name);
  }

  private static String tenant() {
    return "T" + UUID.randomUUID().toString().substring(0, 8);
  }

  /** Letters only, so that nothing else in the shared catalogue can look like it. */
  private static String uniqueName() {
    StringBuilder letters = new StringBuilder("qz");
    for (int i = 0; i < 12; i++) {
      letters.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
    }
    return letters.toString();
  }
}
