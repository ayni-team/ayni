package pe.ayni.recognition;

import org.testcontainers.containers.PostgreSQLContainer;

/** The PostgreSQL recognition's integration tests run against: one container for the whole run. */
final class RecognitionTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private RecognitionTestDatabase() {}
}
