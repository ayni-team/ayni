package pe.ayni.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
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
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.recognition.application.SubmitRecognitionRequestUseCase;
import pe.ayni.recognition.domain.model.InsufficientHours;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Two requests of the same student sent at once, against a real PostgreSQL.
 *
 * <p>A student with exactly the hours for one request who submits twice at the same moment must end
 * with one request, and the other one told how many hours are missing. Without the wait per student,
 * both read the same free session and the database refuses the second as a duplicate, which is an
 * unexplained failure for the student. Take {@code requests.lockStudent} out of the use case and this
 * test fails.
 */
@SpringBootTest
class SubmitRecognitionRequestConcurrencyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  @Autowired private SubmitRecognitionRequestUseCase submit;
  @Autowired private RecognitionRuleRepository rules;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private IdentityApi identity;
  @MockitoBean private SessionsApi sessions;
  @MockitoBean private BookingApi booking;

  @Test
  @DisplayName("two requests at once for the same hours end with one request and one refusal that says what is missing")
  void twoRequestsAtOnce() throws Exception {
    String tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    rules.save(new RecognitionRule(UUID.randomUUID(), tenant, 10, null, LocalDate.of(2026, 1, 1), NOW));
    when(booking.requireBooking(any(UUID.class)))
        .thenAnswer(
            call ->
                new BookingView(
                    call.getArgument(0), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW, NOW, 1, "need", BookingStatus.COMPLETED));

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 8; round++) {
        UUID student = UUID.randomUUID();
        SessionSummary session =
            new SessionSummary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(86400), NOW.minusSeconds(79200), 10);
        when(sessions.completedSessionsOf(student)).thenReturn(List.of(session));

        CyclicBarrier together = new CyclicBarrier(2);
        List<Future<String>> outcomes = new ArrayList<>();
        for (int caller = 0; caller < 2; caller++) {
          Callable<String> attempt =
              () -> {
                together.await();
                try {
                  TenantContext.runAs(tenant, () -> submit.execute(student));
                  return "REGISTERED";
                } catch (InsufficientHours refusal) {
                  return "TOLD " + refusal.getMissingHours();
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
        assertThat(results).containsExactlyInAnyOrder("REGISTERED", "TOLD 10");
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from recognition.requests where tenant_id = ? and student_id = ?",
                    Integer.class,
                    tenant,
                    student))
            .isEqualTo(1);
      }
    } catch (ExecutionException failure) {
      throw new AssertionError(failure);
    } finally {
      pool.shutdownNow();
    }
  }
}
