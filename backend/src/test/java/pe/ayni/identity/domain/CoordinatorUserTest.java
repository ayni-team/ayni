package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;

class CoordinatorUserTest {

    private static final Instant CREATED =
            Instant.parse(
                    "2026-10-02T20:00:00Z");

    private static final Instant ACTIVATED =
            Instant.parse(
                    "2026-10-02T21:00:00Z");

    @Test
    void activatesPendingCoordinator() {

        User coordinator =
                coordinator();

        coordinator.activateCoordinator(
                ACTIVATED);

        assertThat(
                coordinator.getStatus())
                .isEqualTo(
                        UserStatus.ACTIVE);

        assertThat(
                coordinator.getActivatedAt())
                .isEqualTo(
                        ACTIVATED);

        assertThat(
                coordinator.getUpdatedAt())
                .isEqualTo(
                        ACTIVATED);
    }

    @Test
    void cannotActivateCoordinatorTwice() {

        User coordinator =
                coordinator();

        coordinator.activateCoordinator(
                ACTIVATED);

        assertThatThrownBy(
                () ->
                        coordinator.activateCoordinator(
                                ACTIVATED.plusSeconds(10)))
                .isInstanceOf(
                        IdentityRuleViolation.class);
    }

    private User coordinator() {

        return new User(
                UUID.randomUUID(),
                "UPC",
                UserRole.COORDINATOR,
                "coordinator@upc.edu.pe",
                null,
                "Maria Coordinator",
                null,
                null,
                null,
                null,
                UserStatus.PENDING,
                null,
                null,
                CREATED,
                CREATED);
    }
}