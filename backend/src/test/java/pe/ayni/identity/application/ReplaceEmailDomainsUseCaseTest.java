package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.UniversityAlreadyRegistered;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.shared.events.UniversityIdentityChanged;

class ReplaceEmailDomainsUseCaseTest {

    private static final Instant CREATED = Instant.parse("2026-09-21T20:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UUID administrator = UUID.randomUUID();
    private final ReplaceEmailDomainsUseCase useCase =
            new ReplaceEmailDomainsUseCase(tenants, Clock.fixed(NOW, ZoneOffset.UTC), events);

    private Tenant tenant(String code, TenantStatus status, String... domains) {
        return new Tenant(
                UUID.randomUUID(),
                code,
                "University " + code,
                null,
                null,
                null,
                List.of(domains),
                BigDecimal.valueOf(13),
                ZoneId.of("America/Lima"),
                status,
                CREATED);
    }

    @Test
    @DisplayName("replaces the whole list, normalized, after taking the lock of registering")
    void replacesTheWholeList() {
        Tenant upc = tenant("UPC", TenantStatus.ACTIVE, "upc.edu.pe");
        Tenant pucp = tenant("PUCP", TenantStatus.ACTIVE, "pucp.edu.pe");
        when(tenants.findByCode("UPC")).thenReturn(Optional.of(upc));
        when(tenants.findAll()).thenReturn(List.of(upc, pucp));

        UniversityEmailDomainsView view =
                useCase.execute(administrator, " upc ", List.of("UPC.pe", "upc.edu.pe", "upc.pe"));

        assertThat(view.code()).isEqualTo("UPC");
        assertThat(view.emailDomains()).containsExactly("upc.pe", "upc.edu.pe");
        assertThat(upc.getUpdatedAt()).isEqualTo(NOW);

        InOrder order = inOrder(tenants);
        order.verify(tenants).lockUniversityRegistration();
        order.verify(tenants).findByCode("UPC");
        order.verify(tenants).save(upc);
    }

    @Test
    @DisplayName("tells that the identity changed, naming the administrator and the university that was edited")
    void tellsThatTheIdentityChanged() {
        Tenant upc = tenant("UPC", TenantStatus.ACTIVE, "upc.edu.pe");
        when(tenants.findByCode("UPC")).thenReturn(Optional.of(upc));
        when(tenants.findAll()).thenReturn(List.of(upc));

        useCase.execute(administrator, "upc", List.of("upc.pe"));

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue())
                .isEqualTo(new UniversityIdentityChanged("UPC", administrator, NOW));
    }

    @Test
    @DisplayName("a domain held by another university is a conflict, whatever its status")
    void aDomainHeldByAnotherUniversityIsAConflict() {
        Tenant upc = tenant("UPC", TenantStatus.ACTIVE, "upc.edu.pe");
        Tenant suspended = tenant("OLD", TenantStatus.SUSPENDED, "Old.edu.pe");
        when(tenants.findByCode("UPC")).thenReturn(Optional.of(upc));
        when(tenants.findAll()).thenReturn(List.of(upc, suspended));

        assertThatThrownBy(() -> useCase.execute(administrator, "UPC", List.of("upc.edu.pe", "old.edu.pe")))
                .isInstanceOf(UniversityAlreadyRegistered.class);

        assertThat(upc.getEmailDomains()).containsExactly("upc.edu.pe");
        verify(tenants, never()).save(upc);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("keeping its own domains is not a conflict")
    void keepingItsOwnDomainsIsNotAConflict() {
        Tenant upc = tenant("UPC", TenantStatus.ACTIVE, "upc.edu.pe");
        when(tenants.findByCode("UPC")).thenReturn(Optional.of(upc));
        when(tenants.findAll()).thenReturn(List.of(upc));

        assertThat(useCase.execute(administrator, "UPC", List.of("upc.edu.pe")).emailDomains()).containsExactly("upc.edu.pe");
    }

    @Test
    @DisplayName("a university that does not exist is not found")
    void aUniversityThatDoesNotExistIsNotFound() {
        when(tenants.findByCode("NOBODY")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(administrator, "nobody", List.of("nobody.edu.pe")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("an invalid list is refused before the lock is taken")
    void anInvalidListIsRefusedBeforeTheLock() {
        assertThatThrownBy(() -> useCase.execute(administrator, "UPC", List.of()))
                .isInstanceOf(IdentityRuleViolation.class);

        verify(tenants, never()).lockUniversityRegistration();
    }
}
