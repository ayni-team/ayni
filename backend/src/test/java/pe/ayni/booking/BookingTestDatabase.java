package pe.ayni.booking;

import org.testcontainers.containers.PostgreSQLContainer;

/** The PostgreSQL booking's integration tests run against: one container for the whole run. */
final class BookingTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private BookingTestDatabase() {}
}
