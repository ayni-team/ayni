package pe.ayni.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.SessionStarted;

/**
 * Many entries of the same university written at the same instant, each on a connection of its
 * own, against a real PostgreSQL (US48).
 *
 * <p>Every entry must take a place of its own and the hash of the one right before it. Without the
 * university's turn to write ({@code ActivityLogRepository.lockChainOf} in {@code
 * ActivityLogWriter}) several writers read the same end of the chain and claim the same place: all
 * but one are refused by {@code uq_activity_log_sequence}, the listener swallows the failure as it
 * must, and entries go missing. Take the lock out and this test fails.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class ActivityLogChainConcurrencyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = AuditTestDatabase.INSTANCE;

  /** Writers released together; each holds two connections, the publisher's and the entry's. */
  private static final int WRITERS = 8;

  private static final int ROUNDS = 4;

  private static final Instant AT = Instant.parse("2026-10-21T08:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  @Test
  @DisplayName("Entries written at the same time keep the chain whole")
  void entriesWrittenAtTheSameTimeKeepTheChainWhole() throws Exception {
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    String tenant = people.aUniversity("K");
    Person coordinator = people.aCoordinator(tenant);

    ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        // When several sessions start at the same moment, each one is written down by audit
        CyclicBarrier together = new CyclicBarrier(WRITERS);
        List<Future<?>> writers = new ArrayList<>();
        for (int writer = 0; writer < WRITERS; writer++) {
          writers.add(
              pool.submit(
                  () -> {
                    together.await();
                    transactions.executeWithoutResult(
                        status ->
                            events.publishEvent(
                                new SessionStarted(tenant, UUID.randomUUID(), UUID.randomUUID(), AT)));
                    return null;
                  }));
        }
        for (Future<?> writer : writers) {
          writer.get(60, TimeUnit.SECONDS);
        }
      }
    } finally {
      pool.shutdownNow();
    }

    // Then every one of them is there, each in a place of its own, without gaps
    int written = WRITERS * ROUNDS;
    assertThat(jdbc.queryForList("select sequence_number from audit.activity_log where tenant_id = ? order by sequence_number", Long.class, tenant))
        .containsExactlyElementsOf(LongStream.rangeClosed(1, written).boxed().toList());
    // And each one carries the hash of the one before it
    mockMvc
        .perform(get("/api/v1/coordinator/audit-log/integrity").header("Authorization", "Bearer " + coordinator.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intact").value(true))
        .andExpect(jsonPath("$.entriesChecked").value(written));
  }
}
