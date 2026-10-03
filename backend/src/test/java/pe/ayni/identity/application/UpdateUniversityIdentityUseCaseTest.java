package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.tenancy.TenantContext;

class UpdateUniversityIdentityUseCaseTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-02T23:00:00Z");

    private final TenantRepository tenants =
            mock(TenantRepository.class);

    private final UserRepository users =
            mock(UserRepository.class);

    private final UpdateUniversityIdentityUseCase useCase =
            new UpdateUniversityIdentityUseCase(
                    tenants,
                    users,
                    Clock.fixed(
                            NOW,
                            ZoneOffset.UTC));

    @Test
    void activeCoordinatorUpdatesOwnUniversity() {

        UUID coordinatorId =
                UUID.randomUUID();

        User coordinator =
                user(
                        coordinatorId,
                        UserRole.COORDINATOR,
                        UserStatus.ACTIVE);

        Tenant tenant =
                upc();

        when(
                users.findByTenantIdAndId(
                        "UPC",
                        coordinatorId))
                .thenReturn(
                        Optional.of(
                                coordinator));

        when(
                tenants.findByCode(
                        "UPC"))
                .thenReturn(
                        Optional.of(
                                tenant));

        AtomicReference<UniversityIdentityView> result =
                new AtomicReference<>();

        TenantContext.runAs(
                "UPC",
                () ->
                        result.set(
                                useCase.execute(
                                        coordinatorId,
                                        "https://cdn.ayni.pe/upc.png",
                                        "#D50000",
                                        "#FFFFFF")));

        assertThat(
                result.get())
                .isNotNull();

        assertThat(
                result.get().code())
                .isEqualTo("UPC");

        assertThat(
                result.get().logoUrl())
                .isEqualTo(
                        "https://cdn.ayni.pe/upc.png");

        assertThat(
                result.get().primaryColor())
                .isEqualTo(
                        "#D50000");

        assertThat(
                result.get().secondaryColor())
                .isEqualTo(
                        "#FFFFFF");

        assertThat(
                tenant.getUpdatedAt())
                .isEqualTo(NOW);

        verify(tenants)
                .save(tenant);
    }

    @Test
    void studentCannotUpdateUniversityIdentity() {

        UUID studentId =
                UUID.randomUUID();

        when(
                users.findByTenantIdAndId(
                        "UPC",
                        studentId))
                .thenReturn(
                        Optional.of(
                                user(
                                        studentId,
                                        UserRole.STUDENT,
                                        UserStatus.ACTIVE)));

        assertThatThrownBy(
                () ->
                        TenantContext.runAs(
                                "UPC",
                                () ->
                                        useCase.execute(
                                                studentId,
                                                null,
                                                "#000000",
                                                "#FFFFFF")))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "active coordinator");

        verify(
                tenants,
                never())
                .findByCode("UPC");
    }

    @Test
    void restrictedCoordinatorCannotUpdateUniversityIdentity() {

        UUID coordinatorId =
                UUID.randomUUID();

        when(
                users.findByTenantIdAndId(
                        "UPC",
                        coordinatorId))
                .thenReturn(
                        Optional.of(
                                user(
                                        coordinatorId,
                                        UserRole.COORDINATOR,
                                        UserStatus.RESTRICTED)));

        assertThatThrownBy(
                () ->
                        TenantContext.runAs(
                                "UPC",
                                () ->
                                        useCase.execute(
                                                coordinatorId,
                                                null,
                                                "#000000",
                                                "#FFFFFF")))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "active coordinator");

        verify(
                tenants,
                never())
                .findByCode("UPC");
    }

    @Test
    void coordinatorCannotUpdateAnotherUniversity() {

        UUID coordinatorId =
                UUID.randomUUID();

        when(
                users.findByTenantIdAndId(
                        "PUCP",
                        coordinatorId))
                .thenReturn(
                        Optional.empty());

        assertThatThrownBy(
                () ->
                        TenantContext.runAs(
                                "PUCP",
                                () ->
                                        useCase.execute(
                                                coordinatorId,
                                                null,
                                                "#003A70",
                                                "#FFFFFF")))
                .isInstanceOf(
                        java.util.NoSuchElementException.class)
                .hasMessageContaining(
                        "does not belong");

        verify(
                tenants,
                never())
                .findByCode("PUCP");
    }

    private User user(
            UUID id,
            UserRole role,
            UserStatus status) {

        return new User(
                id,
                "UPC",
                role,
                role == UserRole.STUDENT
                        ? "student@upc.edu.pe"
                        : "coordinator@upc.edu.pe",
                role == UserRole.STUDENT
                        ? "U202500003"
                        : null,
                "Ayni User",
                role == UserRole.STUDENT
                        ? "Software Engineering"
                        : null,
                role == UserRole.STUDENT
                        ? "2026-2"
                        : null,
                null,
                null,
                status,
                null,
                status == UserStatus.ACTIVE
                        ? NOW.minusSeconds(3600)
                        : null,
                NOW.minusSeconds(7200),
                NOW.minusSeconds(7200));
    }

    private Tenant upc() {

        return new Tenant(
                UUID.randomUUID(),
                "UPC",
                "Universidad Peruana de Ciencias Aplicadas",
                null,
                null,
                null,
                List.of(
                        "upc.edu.pe"),
                new BigDecimal(
                        "17.00"),
                ZoneId.of(
                        "America/Lima"),
                TenantStatus.ACTIVE,
                NOW.minusSeconds(7200));
    }
}