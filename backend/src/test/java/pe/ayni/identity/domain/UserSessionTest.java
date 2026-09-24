package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.domain.model.UserSession;

class UserSessionTest {

    private static final Instant NOW =
            Instant.parse("2026-09-22T16:00:00Z");

    private UserSession session() {
        return new UserSession(
                UUID.randomUUID(),
                "UPC",
                UUID.randomUUID(),
                "a".repeat(64),
                NOW.plusSeconds(3600),
                NOW);
    }

    @Test
    void activeSessionIsUsableBeforeExpiry() {
        UserSession session = session();

        assertThat(session.isActive(NOW))
                .isTrue();
    }

    @Test
    void expiredSessionIsNotActive() {
        UserSession session = session();

        assertThat(
                session.isActive(
                        NOW.plusSeconds(3600)))
                .isFalse();
    }

    @Test
    void revokedSessionIsNotActive() {
        UserSession session = session();

        session.revoke(NOW.plusSeconds(30));

        assertThat(
                session.isActive(
                        NOW.plusSeconds(60)))
                .isFalse();

        assertThat(session.getRevokedAt())
                .isEqualTo(NOW.plusSeconds(30));
    }
}