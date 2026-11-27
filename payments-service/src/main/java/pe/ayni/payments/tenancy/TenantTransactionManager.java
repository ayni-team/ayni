package pe.ayni.payments.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * A JDBC transaction manager that tells PostgreSQL which university the transaction belongs to,
 * the same rule the monolith applies (TS01, ADR 0007).
 *
 * <p>Every transaction starts with {@code set_config('app.tenant_id', <university>, true)}, the
 * function form of {@code SET LOCAL}: the value lasts until the transaction ends, so a pooled
 * connection carries no university into the next request. Without a university bound the value is
 * empty and every table with a policy answers nothing.
 */
public class TenantTransactionManager extends DataSourceTransactionManager {

  private static final long serialVersionUID = 1L;

  private static final String SET_TENANT = "select set_config('app.tenant_id', ?, true)";

  public TenantTransactionManager(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  protected void prepareTransactionalConnection(Connection connection, TransactionDefinition definition)
      throws SQLException {
    super.prepareTransactionalConnection(connection, definition);
    String tenantId = TenantContext.get();
    try (PreparedStatement statement = connection.prepareStatement(SET_TENANT)) {
      statement.setString(1, tenantId == null ? "" : tenantId);
      statement.execute();
    }
  }
}
