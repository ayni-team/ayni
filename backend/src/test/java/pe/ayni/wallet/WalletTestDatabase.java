package pe.ayni.wallet;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The PostgreSQL the integration tests run against.
 *
 * <p>A real one, and the same version {@code docker compose} uses, because the tests that need a
 * database are about what the database itself does: the trigger that refuses to change the ledger
 * and the constraints that refuse an impossible group of credits. Neither exists in an in-memory
 * database, and a test of a rule that is not there proves nothing.
 *
 * <p>One container for the whole test run. Starting it costs a couple of seconds; starting one per
 * test class would cost that every time.
 */
final class WalletTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private WalletTestDatabase() {}
}
