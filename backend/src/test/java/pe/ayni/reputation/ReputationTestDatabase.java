package pe.ayni.reputation;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The PostgreSQL used by reputation integration tests.
 */
final class ReputationTestDatabase {

    static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        INSTANCE.start();
    }

    private ReputationTestDatabase() {
    }
}