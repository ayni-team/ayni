package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;

class PlatformAdminTest {

    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

    @Test
    @DisplayName("the first administrator is active, with the address normalized and the name trimmed")
    void theFirstAdministratorIsActive() {
        UUID id = UUID.randomUUID();

        PlatformAdmin admin = PlatformAdmin.first(id, "  Admin@Ayni.Example ", " Rosa Quispe ", NOW);

        assertThat(admin.getId()).isEqualTo(id);
        assertThat(admin.getEmail()).isEqualTo("admin@ayni.example");
        assertThat(admin.getFullName()).isEqualTo("Rosa Quispe");
        assertThat(admin.getStatus()).isEqualTo(PlatformAdminStatus.ACTIVE);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest(name = "\"{0}\" is not an address of an administrator")
    @ValueSource(strings = {"", "   ", "admin", "@ayni.example", "admin@", "a@b@ayni.example", "ad min@ayni.example"})
    void aMalformedAddressIsRefused(String email) {
        assertThatThrownBy(() -> PlatformAdmin.first(UUID.randomUUID(), email, "Admin", NOW))
                .isInstanceOf(IdentityRuleViolation.class);
    }

    @Test
    @DisplayName("an address longer than the column is refused")
    void anAddressLongerThanTheColumnIsRefused() {
        String email = "a".repeat(150) + "@ayni.example";

        assertThatThrownBy(() -> PlatformAdmin.first(UUID.randomUUID(), email, "Admin", NOW))
                .isInstanceOf(IdentityRuleViolation.class);
    }

    @Test
    @DisplayName("a blank or too long name is refused")
    void aBlankOrTooLongNameIsRefused() {
        assertThatThrownBy(() -> PlatformAdmin.first(UUID.randomUUID(), "admin@ayni.example", " ", NOW))
                .isInstanceOf(IdentityRuleViolation.class);
        assertThatThrownBy(() -> PlatformAdmin.first(UUID.randomUUID(), "admin@ayni.example", null, NOW))
                .isInstanceOf(IdentityRuleViolation.class);
        assertThatThrownBy(
                        () -> PlatformAdmin.first(UUID.randomUUID(), "admin@ayni.example", "x".repeat(161), NOW))
                .isInstanceOf(IdentityRuleViolation.class);
    }
}
