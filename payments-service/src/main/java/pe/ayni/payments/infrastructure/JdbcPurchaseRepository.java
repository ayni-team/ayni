package pe.ayni.payments.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.payments.application.PurchaseRepository;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseStatus;

/**
 * {@link PurchaseRepository} in plain SQL. The monolith kept purchases with JPA; here a handful of
 * statements do the same work without Hibernate, which this small service cannot afford in memory.
 *
 * <p>Every method is transactional, joining the caller's transaction when there is one: the
 * transaction is what carries the university to PostgreSQL ({@code TenantTransactionManager}), so a
 * statement outside one would see no rows at all.
 */
@Repository
@Transactional
class JdbcPurchaseRepository implements PurchaseRepository {

  private static final String COLUMNS =
      """
      id, tenant_id, student_id, credits, amount, currency, status, provider_reference,
      idempotency_key, confirmed_at, expires_at, created_at
      """;

  private final JdbcClient jdbc;

  JdbcPurchaseRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Purchase save(Purchase purchase) {
    jdbc.sql(
            """
            insert into payments.purchases (%s)
            values (:id, :tenantId, :studentId, :credits, :amount, :currency, :status,
                    :providerReference, :idempotencyKey, :confirmedAt, :expiresAt, :createdAt)
            on conflict (id) do update
              set status = excluded.status,
                  provider_reference = excluded.provider_reference,
                  confirmed_at = excluded.confirmed_at
            """
                .formatted(COLUMNS))
        .param("id", purchase.getId())
        .param("tenantId", purchase.getTenantId())
        .param("studentId", purchase.getStudentId())
        .param("credits", purchase.getCredits())
        .param("amount", purchase.getAmount())
        .param("currency", purchase.getCurrency())
        .param("status", purchase.getStatus().name())
        .param("providerReference", purchase.getProviderReference())
        .param("idempotencyKey", purchase.getIdempotencyKey())
        .param("confirmedAt", utc(purchase.getConfirmedAt()))
        .param("expiresAt", utc(purchase.getExpiresAt()))
        .param("createdAt", utc(purchase.getCreatedAt()))
        .update();
    return purchase;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Purchase> findById(UUID id) {
    return jdbc.sql("select " + COLUMNS + " from payments.purchases where id = :id")
        .param("id", id)
        .query(JdbcPurchaseRepository::purchase)
        .optional();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Purchase> findByTenantIdAndStudentIdAndIdempotencyKey(
      String tenantId, UUID studentId, String idempotencyKey) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from payments.purchases
                where tenant_id = :tenantId and student_id = :studentId
                  and idempotency_key = :idempotencyKey
                """)
        .param("tenantId", tenantId)
        .param("studentId", studentId)
        .param("idempotencyKey", idempotencyKey)
        .query(JdbcPurchaseRepository::purchase)
        .optional();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Purchase> findByTenantIdAndStudentIdAndId(
      String tenantId, UUID studentId, UUID id) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from payments.purchases
                where tenant_id = :tenantId and student_id = :studentId and id = :id
                """)
        .param("tenantId", tenantId)
        .param("studentId", studentId)
        .param("id", id)
        .query(JdbcPurchaseRepository::purchase)
        .optional();
  }

  @Override
  public Optional<Purchase> findByTenantIdAndId(String tenantId, UUID id) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + " from payments.purchases where tenant_id = :tenantId and id = :id for update")
        .param("tenantId", tenantId)
        .param("id", id)
        .query(JdbcPurchaseRepository::purchase)
        .optional();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Purchase> findByTenantIdAndStatusOrderByCreatedAtAsc(
      String tenantId, PurchaseStatus status) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from payments.purchases
                where tenant_id = :tenantId and status = :status
                order by created_at asc
                """)
        .param("tenantId", tenantId)
        .param("status", status.name())
        .query(JdbcPurchaseRepository::purchase)
        .list();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Purchase> findByTenantIdAndStudentIdOrderByCreatedAtDesc(
      String tenantId, UUID studentId) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + """
                from payments.purchases
                where tenant_id = :tenantId and student_id = :studentId
                order by created_at desc
                """)
        .param("tenantId", tenantId)
        .param("studentId", studentId)
        .query(JdbcPurchaseRepository::purchase)
        .list();
  }

  @Override
  @Transactional(readOnly = true)
  public Long sumReservedCredits(String tenantId, UUID studentId, Instant from, Instant to) {
    return jdbc.sql(
            """
            select coalesce(sum(credits), 0)
            from payments.purchases
            where tenant_id = :tenantId
              and student_id = :studentId
              and (
                (status = 'CONFIRMED' and confirmed_at >= :from and confirmed_at < :to)
                or
                (status = 'PENDING'
                  and (
                    (created_at >= :from and created_at < :to)
                    or (created_at < :from and expires_at >= :from)
                  ))
              )
            """)
        .param("tenantId", tenantId)
        .param("studentId", studentId)
        .param("from", utc(from))
        .param("to", utc(to))
        .query(Long.class)
        .single();
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> tenantsWithPendingPurchases() {
    return jdbc.sql("select tenant_id from payments.tenants_with_pending_purchases()")
        .query(String.class)
        .list();
  }

  private static Purchase purchase(ResultSet row, int rowNumber) throws SQLException {
    return Purchase.restore(
        row.getObject("id", UUID.class),
        row.getString("tenant_id"),
        row.getObject("student_id", UUID.class),
        row.getInt("credits"),
        row.getBigDecimal("amount"),
        row.getString("currency"),
        PurchaseStatus.valueOf(row.getString("status")),
        row.getString("provider_reference"),
        row.getString("idempotency_key"),
        instant(row, "confirmed_at"),
        instant(row, "expires_at"),
        instant(row, "created_at"));
  }

  static Instant instant(ResultSet row, String column) throws SQLException {
    OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
