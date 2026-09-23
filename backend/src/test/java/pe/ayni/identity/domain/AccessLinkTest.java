package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.AccessPurpose;

class AccessLinkTest {

    private static final Instant NOW = Instant.parse("2026-09-21T20:00:00Z");

    private AccessLink validLink() {
        return new AccessLink(
                UUID.randomUUID(),
                "UPC",
                "u202612345@upc.edu.pe",
                AccessPurpose.ACTIVATION,
                "a".repeat(64),
                NOW.plusSeconds(900),
                "127.0.0.1",
                NOW);
    }

    @Test
    @DisplayName("a non-expired and non-consumed access link is usable")
    void validAccessLinkIsUsable() {
        AccessLink link = validLink();

        assertThat(link.isUsable(NOW)).isTrue();
    }

    @Test
    @DisplayName("an expired access link is not usable")
    void expiredAccessLinkIsNotUsable() {
        AccessLink link =
                new AccessLink(
                        UUID.randomUUID(),
                        "UPC",
                        "u202612345@upc.edu.pe",
                        AccessPurpose.ACTIVATION,
                        "b".repeat(64),
                        NOW.minusSeconds(1),
                        "127.0.0.1",
                        NOW.minusSeconds(900));

        assertThat(link.isUsable(NOW)).isFalse();
    }

    @Test
    @DisplayName("an access link can be consumed once")
    void accessLinkCanBeConsumedOnce() {
        AccessLink link = validLink();

        link.consume(NOW);

        assertThat(link.getConsumedAt()).isEqualTo(NOW);
        assertThat(link.isUsable(NOW.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("a consumed access link cannot be consumed again")
    void consumedAccessLinkCannotBeUsedTwice() {
        AccessLink link = validLink();

        link.consume(NOW);

        assertThatThrownBy(() -> link.consume(NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Access link is expired or already consumed");
    }

    @Test
    @DisplayName("an expired access link cannot be consumed")
    void expiredAccessLinkCannotBeConsumed() {
        AccessLink link =
                new AccessLink(
                        UUID.randomUUID(),
                        "UPC",
                        "u202612345@upc.edu.pe",
                        AccessPurpose.ACTIVATION,
                        "c".repeat(64),
                        NOW.minusSeconds(1),
                        null,
                        NOW.minusSeconds(900));

        assertThatThrownBy(() -> link.consume(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Access link is expired or already consumed");
    }
}