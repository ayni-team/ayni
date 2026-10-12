package pe.ayni.reputation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Requests that race, sent at once over HTTP against a real PostgreSQL. Both of them start behind a
 * {@link CyclicBarrier}, and each case repeats over fresh sessions so that an unlucky interleaving
 * is not what makes it pass.
 *
 * <p>Take {@code lockByTenantIdAndSessionId} out of the use case and the first test fails: both
 * requests pass the check that the session was not rated, and the database refuses the second one
 * with a failure nobody explained. Take {@code lockByTenantIdAndTutorIdAndCatalogItemId} out and the
 * second one fails: both ratings start from the same count and the second write erases the first.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateSessionConcurrencyTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = ReputationTestDatabase.INSTANCE;

    private static final int ROUNDS = 8;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;

    private final String tenant = "T" + UUID.randomUUID().toString().substring(0, 8);

    private int rate(UUID sessionId, UUID userId, String body) {
        try {
            return mockMvc
                    .perform(
                            post("/api/v1/sessions/{id}/rating", sessionId)
                                    .header("X-Tenant-Id", tenant)
                                    .header("X-User-Id", userId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception failure) {
            // An error nobody mapped never reaches a status: it is the 500 this test exists to prevent.
            return 500;
        }
    }

    /** Runs every attempt behind one barrier and answers their statuses. */
    private List<Integer> atOnce(List<Callable<Integer>> attempts) throws Exception {
        CyclicBarrier together = new CyclicBarrier(attempts.size());
        ExecutorService pool = Executors.newFixedThreadPool(attempts.size());
        try {
            List<Future<Integer>> outcomes = new ArrayList<>();
            for (Callable<Integer> attempt : attempts) {
                outcomes.add(
                        pool.submit(
                                () -> {
                                    together.await();
                                    return attempt.call();
                                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> outcome : outcomes) {
                statuses.add(outcome.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Two ratings of the same session at once end with one rating and one refusal")
    void twoRatingsOfTheSameSessionAtOnce() throws Exception {
        UUID tutor = UUID.randomUUID();
        UUID catalogItem = UUID.randomUUID();

        for (int round = 0; round < ROUNDS; round++) {
            UUID student = UUID.randomUUID();
            UUID session =
                    CompletedSessions.complete(
                            events, transactions, jdbc, tenant, tutor, student, catalogItem);

            List<Integer> statuses =
                    atOnce(
                            List.of(
                                    () -> rate(session, student, "{\"stars\":5}"),
                                    () -> rate(session, student, "{\"stars\":3}")));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from reputation.ratings"
                                            + " where tenant_id = ? and session_id = ?",
                                    Integer.class,
                                    tenant,
                                    session))
                    .isEqualTo(1);
        }

        assertThat(
                        jdbc.queryForObject(
                                "select ratings_count from reputation.tutor_standing"
                                        + " where tenant_id = ? and tutor_id = ? and catalog_item_id = ?",
                                Integer.class,
                                tenant,
                                tutor,
                                catalogItem))
                .isEqualTo(ROUNDS);
    }

    @Test
    @DisplayName("Two students rating the same tutor at once are both counted")
    void twoStudentsRatingTheSameTutorAtOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID tutor = UUID.randomUUID();
            UUID catalogItem = UUID.randomUUID();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            UUID firstSession =
                    CompletedSessions.complete(
                            events, transactions, jdbc, tenant, tutor, first, catalogItem);
            UUID secondSession =
                    CompletedSessions.complete(
                            events, transactions, jdbc, tenant, tutor, second, catalogItem);

            List<Integer> statuses =
                    atOnce(
                            List.of(
                                    () -> rate(firstSession, first, "{\"stars\":5}"),
                                    () -> rate(secondSession, second, "{\"stars\":2}")));

            assertThat(statuses).containsExactly(201, 201);
            var standing =
                    jdbc.queryForMap(
                            "select ratings_count, average_stars from reputation.tutor_standing"
                                    + " where tenant_id = ? and tutor_id = ? and catalog_item_id = ?",
                            tenant,
                            tutor,
                            catalogItem);
            assertThat(standing.get("ratings_count")).isEqualTo(2);
            assertThat(standing.get("average_stars").toString()).isEqualTo("3.50");
        }
    }
}
