package pe.ayni.reputation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.shared.events.SessionCompleted;

/**
 * The same {@code SessionCompleted} delivered twice, against a real PostgreSQL.
 *
 * <p>The listeners are asynchronous, so the test cannot see when the second delivery was handled.
 * It asks instead that the figures hold for two seconds after the first one shows: if the second
 * delivery counted, {@code sessions_taught} reaches 2 within that time and the condition never
 * holds.
 */
@SpringBootTest
class RecordCompletedSessionIdempotencyTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = ReputationTestDatabase.INSTANCE;

    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("A session completed twice is counted once")
    void aSessionCompletedTwiceIsCountedOnce() {
        String tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
        UUID tutor = UUID.randomUUID();
        UUID catalogItem = UUID.randomUUID();
        SessionCompleted completed =
                CompletedSessions.event(tenant, tutor, UUID.randomUUID(), catalogItem);

        CompletedSessions.publish(events, transactions, completed);
        CompletedSessions.publish(events, transactions, completed);

        await()
                .atMost(Duration.ofSeconds(20))
                .during(Duration.ofSeconds(2))
                .until(
                        () ->
                                Integer.valueOf(1)
                                                .equals(
                                                        jdbc.queryForList(
                                                                        "select sessions_taught from"
                                                                                + " reputation.tutor_standing"
                                                                                + " where tenant_id = ? and tutor_id = ?"
                                                                                + " and catalog_item_id = ?",
                                                                        Integer.class,
                                                                        tenant,
                                                                        tutor,
                                                                        catalogItem)
                                                                .stream()
                                                                .findFirst()
                                                                .orElse(null)));

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from reputation.rating_windows"
                                        + " where tenant_id = ? and session_id = ?",
                                Integer.class,
                                tenant,
                                completed.sessionId()))
                .isEqualTo(1);
    }
}
