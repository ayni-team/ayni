package pe.ayni.notifications;

import org.testcontainers.containers.PostgreSQLContainer;

/** The PostgreSQL notifications' integration tests run against: one container for the whole run. */
final class NotificationsTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private NotificationsTestDatabase() {}
}
