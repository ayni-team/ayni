package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;

class BootstrapPlatformAdminUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

    private final PlatformAdminRepository admins = mock(PlatformAdminRepository.class);
    private final BootstrapPlatformAdminUseCase useCase =
            new BootstrapPlatformAdminUseCase(admins, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("creates the administrator, active and created now, when nobody has the address")
    void createsTheAdministrator() {
        when(admins.findByEmailIgnoreCase("admin@ayni.example")).thenReturn(Optional.empty());

        assertThat(useCase.execute(" Admin@Ayni.Example", "Rosa Quispe")).isTrue();

        ArgumentCaptor<PlatformAdmin> saved = ArgumentCaptor.forClass(PlatformAdmin.class);
        verify(admins).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@ayni.example");
        assertThat(saved.getValue().getFullName()).isEqualTo("Rosa Quispe");
        assertThat(saved.getValue().getStatus()).isEqualTo(PlatformAdminStatus.ACTIVE);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("leaves alone an administrator with that address, even a disabled one")
    void leavesAloneAnExistingAdministrator() {
        when(admins.findByEmailIgnoreCase("admin@ayni.example"))
                .thenReturn(
                        Optional.of(
                                new PlatformAdmin(
                                        UUID.randomUUID(),
                                        "admin@ayni.example",
                                        "Someone",
                                        PlatformAdminStatus.DISABLED,
                                        NOW)));

        assertThat(useCase.execute("admin@ayni.example", "Rosa Quispe")).isFalse();

        verify(admins, never()).save(any());
    }

    @Test
    @DisplayName("a malformed address is refused before anything is read")
    void aMalformedAddressIsRefused() {
        assertThatThrownBy(() -> useCase.execute("not-an-address", "Rosa Quispe"))
                .isInstanceOf(IdentityRuleViolation.class);

        verify(admins, never()).findByEmailIgnoreCase(anyString());
        verify(admins, never()).save(any());
    }
}
