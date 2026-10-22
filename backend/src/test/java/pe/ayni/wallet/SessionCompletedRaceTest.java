package pe.ayni.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * TS04: the same session completion delivered twice at the same instant, each in a transaction of
 * its own, as the event publication registry can do when a retry meets a delivery still running.
 *
 * <p>Both transactions wait for each other before they commit, so both listeners start together.
 * The wallet takes the university's turn to append to its ledger before it looks for the credits
 * the session already gave. Without that both look, both find nothing and both credit the tutor;
 * with it the second waits, finds what the first one gave and credits nothing.
 */
@SpringBootTest
class SessionCompletedRaceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = WalletTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  @Autowired private WalletApi wallet;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private IdentityApi identity;

  @Test
  @DisplayName("Two deliveries of the same session completion at once credit the tutor once")
  void twoDeliveriesAtOnceCreditOnce() throws Exception {
    UUID tutor = UUID.randomUUID();
    SessionCompleted completed =
        new SessionCompleted(
            UPC,
            UUID.randomUUID(),
            UUID.randomUUID(),
            tutor,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Credits.of(2),
            Instant.now());
    // The tutor already has an account, so the race is about the credits and not about opening it.
    TenantContext.runAs(
        UPC,
        () -> wallet.grant(tutor, Credits.of(1), CreditType.PURCHASED, null, UUID.randomUUID()));

    CyclicBarrier bothAboutToCommit = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<?>> deliveries = new ArrayList<>();
      for (int delivery = 0; delivery < 2; delivery++) {
        deliveries.add(
            pool.submit(
                () ->
                    transactions.executeWithoutResult(
                        status -> {
                          events.publishEvent(completed);
                          await(bothAboutToCommit);
                        })));
      }
      for (Future<?> delivery : deliveries) {
        delivery.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(TenantContext.callAs(UPC, () -> wallet.balanceOf(tutor).available()))
        .isEqualTo(Credits.of(3));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from wallet.credit_lots where tenant_id = ? and source_id = ?",
                Integer.class,
                UPC,
                completed.sessionId()))
        .isEqualTo(1);
  }

  private static void await(CyclicBarrier barrier) {
    try {
      barrier.await(30, TimeUnit.SECONDS);
    } catch (Exception interrupted) {
      throw new IllegalStateException(interrupted);
    }
  }
}
