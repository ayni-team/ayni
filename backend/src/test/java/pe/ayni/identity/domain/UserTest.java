package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;

class UserTest {

    private static final Instant NOW =
            Instant.parse("2026-09-22T05:00:00Z");

    @Test
    @DisplayName("normalizes the institutional email")
    void normalizesInstitutionalEmail() {
        User user =
                new User(
                        UUID.randomUUID(),
                        "UPC",
                        UserRole.STUDENT,
                        "  U202612345@UPC.EDU.PE  ",
                        "U202612345",
                        "Juan Sanchez",
                        "Software Engineering",
                        "7",
                        null,
                        null,
                        UserStatus.ACTIVE,
                        null,
                        NOW,
                        NOW,
                        NOW);

        assertThat(user.getEmail())
                .isEqualTo("u202612345@upc.edu.pe");
    }

    @Test
    @DisplayName("only an ACTIVE user is active")
    void identifiesAnActiveUser() {
        User user =
                new User(
                        UUID.randomUUID(),
                        "UPC",
                        UserRole.STUDENT,
                        "u202612345@upc.edu.pe",
                        "U202612345",
                        "Juan Sanchez",
                        "Software Engineering",
                        "7",
                        null,
                        null,
                        UserStatus.ACTIVE,
                        null,
                        NOW,
                        NOW,
                        NOW);

        assertThat(user.isActive()).isTrue();
    }
}