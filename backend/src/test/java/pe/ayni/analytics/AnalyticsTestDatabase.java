package pe.ayni.analytics;

import org.testcontainers.containers.PostgreSQLContainer;

/** The PostgreSQL analytics' integration tests run against: one container for the whole run. */
final class AnalyticsTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private AnalyticsTestDatabase() {}
}
