package pe.ayni.config;

import org.testcontainers.containers.PostgreSQLContainer;

/** The PostgreSQL the tests of isolation between universities run against: one for the run. */
final class TenancyTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private TenancyTestDatabase() {}
}
