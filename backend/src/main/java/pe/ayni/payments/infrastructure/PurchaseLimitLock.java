package pe.ayni.payments.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

/** Serializes purchases for one student while their monthly allowance is checked and consumed. */
@Component
public class PurchaseLimitLock {

  private static final String LOCK_SQL =
      "select pg_advisory_xact_lock(hashtextextended(?, 0))";

  private final JdbcTemplate jdbc;

  public PurchaseLimitLock(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void acquire(String tenantId, UUID studentId) {
    String key = tenantId + ":" + studentId;
    jdbc.query(
        LOCK_SQL,
        (ResultSetExtractor<Void>)
            resultSet -> {
              if (!resultSet.next()) {
                throw new IllegalStateException("The purchase allowance lock was not acquired");
              }
              return null;
            },
        key);
  }
}
