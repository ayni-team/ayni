package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.events.AccessRequested;

class RequestAccessUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-09-22T05:00:00Z");

    private static final Duration TTL =
            Duration.ofMinutes(10);

    private final TenantRepository tenants =
            mock(TenantRepository.class);

    private final UserRepository users =
            mock(UserRepository.class);

    private final AccessLinkRepository accessLinks =
            mock(AccessLinkRepository.class);

    private final AccessTokenGenerator tokens =
            mock(AccessTokenGenerator.class);

    private final AccessLinkUrlBuilder links =
            mock(AccessLinkUrlBuilder.class);

    private final ApplicationEventPublisher events =
            mock(ApplicationEventPublisher.class);

    private final RequestAccessUseCase useCase =
            new RequestAccessUseCase(
                    tenants,
                    users,
                    accessLinks,
                    tokens,
                    links,
                    events,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    TTL);

    private Tenant upc() {
        return new Tenant(
                UUID.randomUUID(),
                "UPC",
                "Universidad Peruana de Ciencias Aplicadas",
                null,
                null,
                null,
                List.of("upc.edu.pe"),
                BigDecimal.valueOf(17),
                ZoneId.of("America/Lima"),
                TenantStatus.ACTIVE,
                NOW);
    }

    @Test
    @DisplayName("creates an activation link for a new student")
    void createsActivationLinkForNewStudent() {
        when(tenants.findByStatus(TenantStatus.ACTIVE))
                .thenReturn(List.of(upc()));

        when(users.existsByTenantIdAndEmailIgnoreCase(
                "UPC", "u202612345@upc.edu.pe"))
                .thenReturn(false);

        when(tokens.generate())
                .thenReturn(
                        new GeneratedAccessToken(
                                "raw-token",
                                "a".repeat(64)));

        when(links.build("raw-token"))
                .thenReturn(
                        "https://example.test/access/confirm?token=raw-token");

        when(accessLinks.save(any()))
                .thenAnswer(call -> call.getArgument(0));

        useCase.execute(
                "U202612345@UPC.EDU.PE",
                "127.0.0.1");

        ArgumentCaptor<AccessLink> saved =
                ArgumentCaptor.forClass(AccessLink.class);

        verify(accessLinks).save(saved.capture());

        assertThat(saved.getValue().getTenantId())
                .isEqualTo("UPC");

        assertThat(saved.getValue().getEmail())
                .isEqualTo("u202612345@upc.edu.pe");

        assertThat(saved.getValue().getTokenHash())
                .isEqualTo("a".repeat(64));

        assertThat(saved.getValue().getExpiresAt())
                .isEqualTo(NOW.plus(TTL));

        ArgumentCaptor<AccessRequested> published =
                ArgumentCaptor.forClass(AccessRequested.class);

        verify(events).publishEvent(published.capture());

        assertThat(published.getValue().tenantId())
                .isEqualTo("UPC");

        assertThat(published.getValue().purpose())
                .isEqualTo("ACTIVATION");
    }

    @Test
    @DisplayName("creates a login link for an existing student")
    void createsLoginLinkForExistingStudent() {
        when(tenants.findByStatus(TenantStatus.ACTIVE))
                .thenReturn(List.of(upc()));

        when(users.existsByTenantIdAndEmailIgnoreCase(
                "UPC", "u202612345@upc.edu.pe"))
                .thenReturn(true);

        when(tokens.generate())
                .thenReturn(
                        new GeneratedAccessToken(
                                "raw-token",
                                "b".repeat(64)));

        when(links.build("raw-token"))
                .thenReturn("https://example.test/access");

        useCase.execute(
                "u202612345@upc.edu.pe",
                null);

        ArgumentCaptor<AccessRequested> published =
                ArgumentCaptor.forClass(AccessRequested.class);

        verify(events).publishEvent(published.capture());

        assertThat(published.getValue().purpose())
                .isEqualTo("LOGIN");
    }

    @Test
    @DisplayName("rejects an email whose institution is not affiliated")
    void rejectsUnaffiliatedInstitution() {
        when(tenants.findByStatus(TenantStatus.ACTIVE))
                .thenReturn(List.of(upc()));

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "student@gmail.com",
                                "127.0.0.1"))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining("not affiliated");

        verify(tokens, never()).generate();
        verify(accessLinks, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}