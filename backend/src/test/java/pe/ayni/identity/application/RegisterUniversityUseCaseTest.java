package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.domain.model.CreditPolicy;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.infrastructure.CreditPolicyRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.shared.events.UniversityRegistered;

class RegisterUniversityUseCaseTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-02T23:00:00Z");

    private final TenantRepository tenants =
            mock(TenantRepository.class);

    private final CreditPolicyRepository policies =
            mock(CreditPolicyRepository.class);

    private final ApplicationEventPublisher events =
            mock(ApplicationEventPublisher.class);

    private final RegisterUniversityUseCase useCase =
            new RegisterUniversityUseCase(
                    tenants,
                    policies,
                    events,
                    Clock.fixed(
                            NOW,
                            ZoneOffset.UTC));

    @Test
    void registersUniversityWithInitialPolicy() {

        UUID adminId =
                UUID.randomUUID();

        when(
                tenants.findByCode("PUCP"))
                .thenReturn(
                        Optional.empty());

        when(
                tenants.findAll())
                .thenReturn(
                        List.of());

        UniversityAdminView result =
                useCase.execute(
                        adminId,
                        "pucp",
                        "Pontificia Universidad Católica del Perú",
                        null,
                        "#003A70",
                        "#FFFFFF",
                        List.of(
                                "pucp.edu.pe"),
                        new BigDecimal("14.00"),
                        "America/Lima",
                        5,
                        30);

        ArgumentCaptor<Tenant> tenantCaptor =
                ArgumentCaptor.forClass(
                        Tenant.class);

        verify(tenants)
                .save(
                        tenantCaptor.capture());

        Tenant tenant =
                tenantCaptor.getValue();

        assertThat(
                tenant.getCode())
                .isEqualTo("PUCP");

        assertThat(
                tenant.getEmailDomains())
                .containsExactly(
                        "pucp.edu.pe");

        ArgumentCaptor<CreditPolicy> policyCaptor =
                ArgumentCaptor.forClass(
                        CreditPolicy.class);

        verify(policies)
                .save(
                        policyCaptor.capture());

        CreditPolicy policy =
                policyCaptor.getValue();

        assertThat(
                policy.getTenantId())
                .isEqualTo("PUCP");

        assertThat(
                policy.getKind())
                .isEqualTo(
                        PolicyKind.BASELINE);

        assertThat(
                policy.getCreditsAmount())
                .isEqualTo(5);

        assertThat(
                policy.getValidityDays())
                .isEqualTo(30);

        assertThat(
                policy.getCreatedByAdmin())
                .isEqualTo(adminId);

        ArgumentCaptor<UniversityRegistered> eventCaptor =
                ArgumentCaptor.forClass(
                        UniversityRegistered.class);

        verify(events)
                .publishEvent(
                        eventCaptor.capture());

        assertThat(
                eventCaptor.getValue()
                        .tenantId())
                .isEqualTo("PUCP");

        assertThat(result.code())
                .isEqualTo("PUCP");
    }

    @Test
    void rejectsDuplicatedUniversityCode() {

        Tenant existing =
                mock(Tenant.class);

        when(
                tenants.findByCode("UPC"))
                .thenReturn(
                        Optional.of(
                                existing));

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                UUID.randomUUID(),
                                "UPC",
                                "University",
                                null,
                                null,
                                null,
                                List.of(
                                        "example.edu.pe"),
                                new BigDecimal(
                                        "13.00"),
                                "America/Lima",
                                5,
                                30))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "already exists");
    }

    @Test
    void rejectsDuplicatedInstitutionalDomain() {

        Tenant existing =
                mock(Tenant.class);

        when(
                tenants.findByCode("PUCP"))
                .thenReturn(
                        Optional.empty());

        when(
                existing.getEmailDomains())
                .thenReturn(
                        List.of(
                                "pucp.edu.pe"));

        when(
                tenants.findAll())
                .thenReturn(
                        List.of(
                                existing));

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                UUID.randomUUID(),
                                "PUCP",
                                "University",
                                null,
                                null,
                                null,
                                List.of(
                                        "PUCP.EDU.PE"),
                                new BigDecimal(
                                        "13.00"),
                                "America/Lima",
                                5,
                                30))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "domain");
    }
}