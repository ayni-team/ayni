package pe.ayni.matching;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The PostgreSQL the integration tests run against.
 *
 * <p>Same reasoning as {@code pe.ayni.wallet.WalletTestDatabase}: a real database, because the read
 * projection's ordering and filtering queries are exactly what would be untested against an
 * in-memory substitute.
 */
final class MatchingTestDatabase {

    static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        INSTANCE.start();
    }

    private MatchingTestDatabase() {}
}