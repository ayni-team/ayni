package pe.ayni.config.demo;

import org.testcontainers.containers.PostgreSQLContainer;

/** The PostgreSQL the tests of the demo profile run against: one container for the whole run. */
final class DemoTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private DemoTestDatabase() {}
}
