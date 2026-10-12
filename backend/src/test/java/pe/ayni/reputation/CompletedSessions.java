package pe.ayni.reputation;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.SessionCompleted;

/**
 * Opens rating windows the way production does: sessions announce {@code SessionCompleted} and
 * reputation reacts. Rows are never inserted by hand, so a test that passes proves the projection.
 */
final class CompletedSessions {

    private CompletedSessions() {
    }

    static SessionCompleted event(String tenantId, UUID tutorId, UUID studentId, UUID catalogItemId) {
        return new SessionCompleted(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                tutorId,
                studentId,
                catalogItemId,
                Credits.of(1),
                Instant.now());
    }

    static void publish(
            ApplicationEventPublisher events, TransactionTemplate transactions, SessionCompleted event) {
        transactions.executeWithoutResult(status -> events.publishEvent(event));
    }

    /** Completes a session and waits until reputation opened its window. */
    static UUID complete(
            ApplicationEventPublisher events,
            TransactionTemplate transactions,
            JdbcTemplate jdbc,
            String tenantId,
            UUID tutorId,
            UUID studentId,
            UUID catalogItemId) {

        SessionCompleted event = event(tenantId, tutorId, studentId, catalogItemId);
        publish(events, transactions, event);
        await()
                .atMost(Duration.ofSeconds(15))
                .until(
                        () ->
                                jdbc.queryForObject(
                                                "select count(*) from reputation.rating_windows"
                                                        + " where tenant_id = ? and session_id = ?",
                                                Integer.class,
                                                tenantId,
                                                event.sessionId())
                                        == 1);
        return event.sessionId();
    }
}
