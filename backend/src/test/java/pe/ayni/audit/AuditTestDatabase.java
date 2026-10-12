package pe.ayni.audit;

import org.testcontainers.containers.PostgreSQLContainer;

/** The PostgreSQL audit's integration tests run against: one container for the whole run. */
final class AuditTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private AuditTestDatabase() {}
}
