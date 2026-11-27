package pe.ayni.payments.infrastructure.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code payments.outbox}: one row per fact owed to the monolith, written with the change that
 * produced it and marked sent once the monolith accepted it.
 *
 * <p>Like the purchases, the rows belong to a university and row level security applies to them:
 * the relay binds each university in turn, and learns which ones have something to send through a
 * function of the owner that answers only their codes.
 */
@Repository
@Transactional
class OutboxRepository {

  private final JdbcClient jdbc;

  OutboxRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Records a fact; the same event of the same purchase is recorded once, whoever asks again. */
  void record(
      UUID id, String tenantId, String eventType, UUID aggregateId, String payload, Instant now) {
    jdbc.sql(
            """
            insert into payments.outbox
              (id, tenant_id, event_type, aggregate_id, payload, created_at, attempts,
               next_attempt_at)
            values (:id, :tenantId, :eventType, :aggregateId, cast(:payload as jsonb), :now, 0, :now)
            on conflict (event_type, aggregate_id) do nothing
            """)
        .param("id", id)
        .param("tenantId", tenantId)
        .param("eventType", eventType)
        .param("aggregateId", aggregateId)
        .param("payload", payload)
        .param("now", JdbcTime.utc(now))
        .update();
  }

  @Transactional(readOnly = true)
  List<String> tenantsWithDueMessages(Instant now) {
    return jdbc.sql("select tenant_id from payments.tenants_with_unsent_events(:now)")
        .param("now", JdbcTime.utc(now))
        .query(String.class)
        .list();
  }

  /** The oldest messages of the bound university whose next attempt is due. */
  @Transactional(readOnly = true)
  List<OutboxMessage> due(String tenantId, Instant now, int limit) {
    return jdbc.sql(
            """
            select id, tenant_id, event_type, payload::text as payload, attempts
            from payments.outbox
            where tenant_id = :tenantId and sent_at is null and next_attempt_at <= :now
            order by created_at
            limit :limit
            """)
        .param("tenantId", tenantId)
        .param("now", JdbcTime.utc(now))
        .param("limit", limit)
        .query(
            (row, number) ->
                new OutboxMessage(
                    row.getObject("id", UUID.class),
                    row.getString("tenant_id"),
                    row.getString("event_type"),
                    row.getString("payload"),
                    row.getInt("attempts")))
        .list();
  }

  void markSent(UUID id, Instant now) {
    jdbc.sql(
            """
            update payments.outbox
            set sent_at = :now, attempts = attempts + 1, last_error = null
            where id = :id
            """)
        .param("id", id)
        .param("now", JdbcTime.utc(now))
        .update();
  }

  void markFailed(UUID id, Instant nextAttempt, String error) {
    jdbc.sql(
            """
            update payments.outbox
            set attempts = attempts + 1, next_attempt_at = :nextAttempt, last_error = :error
            where id = :id
            """)
        .param("id", id)
        .param("nextAttempt", JdbcTime.utc(nextAttempt))
        .param("error", error == null ? null : error.substring(0, Math.min(error.length(), 500)))
        .update();
  }
}
