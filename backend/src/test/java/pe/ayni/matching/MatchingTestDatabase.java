package pe.ayni.matching;

import org.testcontainers.containers.PostgreSQLContainer;

/** The PostgreSQL matching's integration tests run against: one container for the whole run. */
final class MatchingTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private MatchingTestDatabase() {}
}
