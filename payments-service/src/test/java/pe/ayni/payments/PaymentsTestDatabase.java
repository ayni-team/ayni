package pe.ayni.payments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** One PostgreSQL for every test of the service that needs the database. */
public final class PaymentsTestDatabase {

  public static final PostgreSQLContainer<?> INSTANCE =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("ayni_payments");

  static {
    INSTANCE.start();
  }

  private PaymentsTestDatabase() {}

  /**
   * The container's own user, which row level security does not restrict: what a test reads to
   * check what the service wrote, whatever university it belongs to. The service never uses it.
   */
  public static JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            INSTANCE.getJdbcUrl(), INSTANCE.getUsername(), INSTANCE.getPassword()));
  }
}
