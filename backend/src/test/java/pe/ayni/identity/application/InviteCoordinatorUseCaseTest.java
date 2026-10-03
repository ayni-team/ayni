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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.AccessPurpose;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.events.AccessRequested;

class InviteCoordinatorUseCaseTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-02T23:30:00Z");

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

    private final InviteCoordinatorUseCase useCase =
            new InviteCoordinatorUseCase(
                    tenants,
                    users,
                    accessLinks,
                    tokens,
                    links,
                    events,
                    Clock.fixed(
                            NOW,
                            ZoneOffset.UTC),
                    Duration.ofMinutes(10));

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
                NOW.minusSeconds(3600));
    }

    @Test
    void createsPendingCoordinatorAndInvitation() {

        when(
                tenants.findByCodeAndStatus(
                        "UPC",
                        TenantStatus.ACTIVE))
                .thenReturn(
                        Optional.of(
                                upc()));

        when(
                users.existsByTenantIdAndEmailIgnoreCase(
                        "UPC",
                        "coordinator@upc.edu.pe"))
                .thenReturn(false);

        when(tokens.generate())
                .thenReturn(
                        new GeneratedAccessToken(
                                "raw-token",
                                "a".repeat(64)));

        when(
                links.build(
                        "raw-token"))
                .thenReturn(
                        "https://example.test/access/confirm?token=raw-token");

        useCase.execute(
                "upc",
                "COORDINATOR@UPC.EDU.PE",
                "Maria Coordinator",
                "127.0.0.1");

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(
                        User.class);

        verify(users)
                .save(
                        userCaptor.capture());

        User coordinator =
                userCaptor.getValue();

        assertThat(
                coordinator.getTenantId())
                .isEqualTo("UPC");

        assertThat(
                coordinator.getRole())
                .isEqualTo(
                        UserRole.COORDINATOR);

        assertThat(
                coordinator.getStatus())
                .isEqualTo(
                        UserStatus.PENDING);

        assertThat(
                coordinator.getEmail())
                .isEqualTo(
                        "coordinator@upc.edu.pe");

        assertThat(
                coordinator.getFullName())
                .isEqualTo(
                        "Maria Coordinator");

        ArgumentCaptor<AccessLink> linkCaptor =
                ArgumentCaptor.forClass(
                        AccessLink.class);

        verify(accessLinks)
                .save(
                        linkCaptor.capture());

        assertThat(
                linkCaptor.getValue()
                        .getPurpose())
                .isEqualTo(
                        AccessPurpose.COORDINATOR_INVITE);

        ArgumentCaptor<AccessRequested> eventCaptor =
                ArgumentCaptor.forClass(
                        AccessRequested.class);

        verify(events)
                .publishEvent(
                        eventCaptor.capture());

        assertThat(
                eventCaptor.getValue()
                        .purpose())
                .isEqualTo(
                        "COORDINATOR_INVITE");
    }

    @Test
    void rejectsEmailOutsideUniversityDomain() {

        when(
                tenants.findByCodeAndStatus(
                        "UPC",
                        TenantStatus.ACTIVE))
                .thenReturn(
                        Optional.of(
                                upc()));

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "UPC",
                                "someone@gmail.com",
                                "Coordinator",
                                null))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "does not belong");

        verify(users, never())
                .save(any());

        verify(accessLinks, never())
                .save(any());
    }

    @Test
    void rejectsExistingUniversityUser() {

        when(
                tenants.findByCodeAndStatus(
                        "UPC",
                        TenantStatus.ACTIVE))
                .thenReturn(
                        Optional.of(
                                upc()));

        when(
                users.existsByTenantIdAndEmailIgnoreCase(
                        "UPC",
                        "coordinator@upc.edu.pe"))
                .thenReturn(true);

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "UPC",
                                "coordinator@upc.edu.pe",
                                "Coordinator",
                                null))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "already exists");

        verify(tokens, never())
                .generate();
    }
}