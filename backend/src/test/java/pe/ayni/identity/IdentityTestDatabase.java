package pe.ayni.identity;

import org.testcontainers.containers.PostgreSQLContainer;

/** The PostgreSQL identity's integration tests run against: one container for the whole run. */
final class IdentityTestDatabase {

    static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        INSTANCE.start();
    }

    private IdentityTestDatabase() {}
}
