package pe.ayni.config;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The PostgreSQL the tests of reliable delivery run against (TS04): one for the run, and theirs
 * alone.
 *
 * <p>Not shared with any other test class, because starting the application again delivers every
 * incomplete publication the database holds, and those of other classes would be delivered with
 * them.
 */
final class EventsTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private EventsTestDatabase() {}
}
