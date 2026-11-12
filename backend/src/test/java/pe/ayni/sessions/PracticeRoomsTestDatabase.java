package pe.ayni.sessions;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The PostgreSQL of the practice rooms' tests, of their own: the test that proves a practice leaves
 * no trace compares every table before and after it, and no other test class may write in between.
 */
final class PracticeRoomsTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private PracticeRoomsTestDatabase() {}
}
