package pe.ayni.sessions;

import org.testcontainers.containers.PostgreSQLContainer;

/** The PostgreSQL sessions' integration tests run against: one container for the whole run. */
final class SessionsTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private SessionsTestDatabase() {}
}
