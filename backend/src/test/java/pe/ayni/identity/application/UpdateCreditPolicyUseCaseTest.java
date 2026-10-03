package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.CreditPolicy;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.CreditPolicyRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.tenancy.TenantContext;

class UpdateCreditPolicyUseCaseTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-03T01:00:00Z");

    private final CreditPolicyRepository policies =
            mock(
                    CreditPolicyRepository.class);

    private final TenantRepository tenants =
            mock(
                    TenantRepository.class);

    private final UserRepository users =
            mock(
                    UserRepository.class);

    private final UpdateCreditPolicyUseCase useCase =
            new UpdateCreditPolicyUseCase(
                    policies,
                    tenants,
                    users,
                    Clock.fixed(
                            NOW,
                            ZoneOffset.UTC));

    @Test
    void coordinatorSupersedesBaselinePolicy() {

        UUID coordinatorId =
                UUID.randomUUID();

        User coordinator =
                coordinator(
                        coordinatorId,
                        UserStatus.ACTIVE);

        Tenant tenant =
                upc();

        CreditPolicy previous =
                previousPolicy();

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

        when(
                policies.findByTenantIdAndKindAndSupersededAtIsNull(
                        "UPC",
                        PolicyKind.BASELINE))
                .thenReturn(
                        Optional.of(
                                previous));

        AtomicReference<CreditPolicyConfigurationView> result =
                new AtomicReference<>();

        TenantContext.runAs(
                "UPC",
                () ->
                        result.set(
                                useCase.execute(
                                        coordinatorId,
                                        8,
                                        45)));

        assertThat(
                previous.isCurrent())
                .isFalse();

        assertThat(
                previous.getSupersededAt())
                .isEqualTo(NOW);

        verify(policies)
                .save(previous);

        verify(policies)
                .save(
                        argThat(
                                policy ->
                                        policy != previous
                                                && policy.getTenantId()
                                                .equals("UPC")
                                                && policy.getKind()
                                                == PolicyKind.BASELINE
                                                && policy.getCreditsAmount()
                                                == 8
                                                && policy.getValidityDays()
                                                == 45
                                                && policy.getCreatedByAdmin()
                                                == null
                                                && coordinatorId.equals(
                                                policy.getCreatedByUser())
                                                && policy.getSupersededAt()
                                                == null));

        assertThat(
                result.get())
                .isNotNull();

        assertThat(
                result.get().creditsAmount())
                .isEqualTo(8);

        assertThat(
                result.get().validityDays())
                .isEqualTo(45);

        /*
         * NOW is 2026-10-03 01:00 UTC, but still 2026-10-02
         * for the university in America/Lima.
         */
        assertThat(
                result.get().validFrom())
                .isEqualTo(
                        LocalDate.of(
                                2026,
                                10,
                                2));
    }

    @Test
    void studentCannotChangeCreditPolicy() {

        UUID studentId =
                UUID.randomUUID();

        when(
                users.findByTenantIdAndId(
                        "UPC",
                        studentId))
                .thenReturn(
                        Optional.of(
                                student(
                                        studentId)));

        assertThatThrownBy(
                () ->
                        TenantContext.runAs(
                                "UPC",
                                () ->
                                        useCase.execute(
                                                studentId,
                                                8,
                                                45)))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "active coordinator");

        verify(
                policies,
                never())
                .findByTenantIdAndKindAndSupersededAtIsNull(
                        "UPC",
                        PolicyKind.BASELINE);
    }

    @Test
    void restrictedCoordinatorCannotChangeCreditPolicy() {

        UUID coordinatorId =
                UUID.randomUUID();

        when(
                users.findByTenantIdAndId(
                        "UPC",
                        coordinatorId))
                .thenReturn(
                        Optional.of(
                                coordinator(
                                        coordinatorId,
                                        UserStatus.RESTRICTED)));

        assertThatThrownBy(
                () ->
                        TenantContext.runAs(
                                "UPC",
                                () ->
                                        useCase.execute(
                                                coordinatorId,
                                                8,
                                                45)))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "active coordinator");

        verify(
                policies,
                never())
                .findByTenantIdAndKindAndSupersededAtIsNull(
                        "UPC",
                        PolicyKind.BASELINE);
    }

    @Test
    void universityMustAlreadyHaveBaselinePolicy() {

        UUID coordinatorId =
                UUID.randomUUID();

        when(
                users.findByTenantIdAndId(
                        "UPC",
                        coordinatorId))
                .thenReturn(
                        Optional.of(
                                coordinator(
                                        coordinatorId,
                                        UserStatus.ACTIVE)));

        when(
                tenants.findByCode(
                        "UPC"))
                .thenReturn(
                        Optional.of(
                                upc()));

        when(
                policies.findByTenantIdAndKindAndSupersededAtIsNull(
                        "UPC",
                        PolicyKind.BASELINE))
                .thenReturn(
                        Optional.empty());

        assertThatThrownBy(
                () ->
                        TenantContext.runAs(
                                "UPC",
                                () ->
                                        useCase.execute(
                                                coordinatorId,
                                                8,
                                                45)))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "does not have a baseline");

        verify(
                policies,
                never())
                .save(
                        argThat(
                                policy ->
                                        true));
    }

    private CreditPolicy previousPolicy() {

        return new CreditPolicy(
                UUID.randomUUID(),
                "UPC",
                PolicyKind.BASELINE,
                5,
                30,
                LocalDate.of(
                        2026,
                        9,
                        1),
                UUID.randomUUID(),
                null,
                NOW.minusSeconds(
                        2_592_000));
    }

    private User coordinator(
            UUID id,
            UserStatus status) {

        return new User(
                id,
                "UPC",
                UserRole.COORDINATOR,
                "coordinator@upc.edu.pe",
                null,
                "University Coordinator",
                null,
                null,
                null,
                null,
                status,
                null,
                status == UserStatus.ACTIVE
                        ? NOW.minusSeconds(7200)
                        : null,
                NOW.minusSeconds(7200),
                NOW.minusSeconds(7200));
    }

    private User student(
            UUID id) {

        return new User(
                id,
                "UPC",
                UserRole.STUDENT,
                "student@upc.edu.pe",
                "U202500003",
                "Student",
                "Software Engineering",
                "2026-2",
                null,
                null,
                UserStatus.ACTIVE,
                null,
                NOW.minusSeconds(7200),
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
                NOW.minusSeconds(86_400));
    }
}