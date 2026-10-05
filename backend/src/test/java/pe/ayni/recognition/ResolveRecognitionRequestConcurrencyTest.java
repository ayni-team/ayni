package pe.ayni.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import pe.ayni.recognition.application.ResolveRecognitionRequestUseCase;
import pe.ayni.recognition.application.ResolveRecognitionRequestUseCase.Decision;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RecognitionStateConflict;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Two coordinators deciding the same request at once, against a real PostgreSQL.
 *
 * <p>One of them must decide it and the other must be told it was already decided, and what the first
 * one decided and why must be what stays. Without the lock on the request both read it as waiting, both
 * decide, and the later one silently overwrites the first one's decision and reason. Take {@code
 * requests.lockByIdAndTenantId} out of the use case and this test fails.
 */
@SpringBootTest
class ResolveRecognitionRequestConcurrencyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  @Autowired private ResolveRecognitionRequestUseCase resolve;
  @Autowired private RecognitionRequestRepository requests;
  @Autowired private RequestedSessionRepository requestedSessions;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;

  @Test
  @DisplayName("two decisions at once end with one decision and one refusal, and the first one stays")
  void twoDecisionsAtOnce() throws Exception {
    String tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    UUID coordinator = UUID.randomUUID();
    when(identity.requireUser(any(UUID.class)))
        .thenReturn(new UserView(coordinator, tenant, UserRole.COORDINATOR, "c@u.pe", null, "C", null, null, null));

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 8; round++) {
        RequestedSession session =
            RequestedSession.of(UUID.randomUUID(), tenant, 20, UUID.randomUUID(), NOW.minusSeconds(7200), NOW.minusSeconds(3600), null);
        RecognitionRequest request =
            requests.save(RecognitionRequest.submit(UUID.randomUUID(), tenant, UUID.randomUUID(), List.of(session), NOW));
        requestedSessions.save(session);

        CyclicBarrier together = new CyclicBarrier(2);
        List<Future<String>> outcomes = new ArrayList<>();
        for (Decision decision : new Decision[] {Decision.APPROVE, Decision.REJECT}) {
          Callable<String> attempt =
              () -> {
                together.await();
                try {
                  TenantContext.runAs(tenant, () -> resolve.execute(coordinator, request.getId(), decision, "Reason " + decision));
                  return decision.name();
                } catch (RecognitionStateConflict conflict) {
                  return "REFUSED";
                } catch (RuntimeException failure) {
                  return "FAILED " + failure.getClass().getSimpleName();
                }
              };
          outcomes.add(pool.submit(attempt));
        }

        List<String> results = new ArrayList<>();
        for (Future<String> outcome : outcomes) {
          results.add(outcome.get());
        }
        assertThat(results).contains("REFUSED").hasSize(2);
        String winner = results.stream().filter(result -> !result.equals("REFUSED")).findFirst().orElseThrow();
        String stored =
            jdbc.queryForObject("select status || '/' || decision_reason from recognition.requests where id = ?", String.class, request.getId());
        assertThat(stored).isEqualTo((winner.equals("APPROVE") ? "APPROVED" : "REJECTED") + "/Reason " + winner);
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
