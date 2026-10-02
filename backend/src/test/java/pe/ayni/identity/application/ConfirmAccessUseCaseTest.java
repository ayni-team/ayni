package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.AccessPurpose;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.CoordinatorActivated;

class ConfirmAccessUseCaseTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-02T23:30:00Z");

    private static final String HASH =
            "a".repeat(64);

    private AccessLinkRepository accessLinks;
    private UserRepository users;
    private UserSessionRepository sessions;
    private AccessTokenGenerator tokens;
    private ApplicationEventPublisher events;

    private ConfirmAccessUseCase useCase;

    @BeforeEach
    void setUp() {

        accessLinks =
                mock(
                        AccessLinkRepository.class);

        users =
                mock(
                        UserRepository.class);

        sessions =
                mock(
                        UserSessionRepository.class);

        tokens =
                mock(
                        AccessTokenGenerator.class);

        events =
                mock(
                        ApplicationEventPublisher.class);

        when(
                tokens.hash(
                        "raw-token"))
                .thenReturn(HASH);

        when(tokens.generate())
                .thenReturn(
                        new GeneratedAccessToken(
                                "session-token",
                                "b".repeat(64)));

        useCase =
                new ConfirmAccessUseCase(
                        accessLinks,
                        users,
                        sessions,
                        tokens,
                        events,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC),
                        Duration.ofDays(7));
    }

    @Test
    void loginOpensSession() {

        UUID userId =
                UUID.randomUUID();

        AccessLink link =
                link(
                        "student@upc.edu.pe",
                        AccessPurpose.LOGIN);

        User user =
                new User(
                        userId,
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
                        NOW.minusSeconds(3600),
                        NOW.minusSeconds(3600),
                        NOW.minusSeconds(3600));

        when(
                users.findByTenantIdAndEmailIgnoreCase(
                        "UPC",
                        "student@upc.edu.pe"))
                .thenReturn(
                        Optional.of(user));

        when(
                accessLinks.consume(
                        link.getId(),
                        NOW))
                .thenReturn(1);

        ConfirmAccessResult result =
                useCase.execute(
                        "raw-token");

        assertThat(
                result.userId())
                .isEqualTo(userId);

        verify(sessions)
                .save(
                        any(UserSession.class));
    }

    @Test
    void coordinatorInvitationActivatesCoordinator() {

        UUID coordinatorId =
                UUID.randomUUID();

        AccessLink link =
                link(
                        "coordinator@upc.edu.pe",
                        AccessPurpose.COORDINATOR_INVITE);

        User coordinator =
                new User(
                        coordinatorId,
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
                        NOW.minusSeconds(3600),
                        NOW.minusSeconds(3600));

        when(
                users.findByTenantIdAndEmailIgnoreCase(
                        "UPC",
                        "coordinator@upc.edu.pe"))
                .thenReturn(
                        Optional.of(
                                coordinator));

        when(
                accessLinks.consume(
                        link.getId(),
                        NOW))
                .thenReturn(1);

        ConfirmAccessResult result =
                useCase.execute(
                        "raw-token");

        assertThat(
                coordinator.getStatus())
                .isEqualTo(
                        UserStatus.ACTIVE);

        assertThat(
                coordinator.getActivatedAt())
                .isEqualTo(NOW);

        verify(users)
                .saveAndFlush(
                        coordinator);

        ArgumentCaptor<CoordinatorActivated> eventCaptor =
                ArgumentCaptor.forClass(
                        CoordinatorActivated.class);

        verify(events)
                .publishEvent(
                        eventCaptor.capture());

        assertThat(
                eventCaptor.getValue()
                        .tenantId())
                .isEqualTo("UPC");

        assertThat(
                eventCaptor.getValue()
                        .userId())
                .isEqualTo(
                        coordinatorId);

        assertThat(
                result.userId())
                .isEqualTo(
                        coordinatorId);

        verify(sessions)
                .save(
                        any(UserSession.class));
    }

    @Test
    void consumedCoordinatorInvitationActivatesNobody() {

        AccessLink link =
                link(
                        "coordinator@upc.edu.pe",
                        AccessPurpose.COORDINATOR_INVITE);

        User coordinator =
                new User(
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
                        NOW.minusSeconds(3600),
                        NOW.minusSeconds(3600));

        when(
                users.findByTenantIdAndEmailIgnoreCase(
                        "UPC",
                        "coordinator@upc.edu.pe"))
                .thenReturn(
                        Optional.of(
                                coordinator));

        when(
                accessLinks.consume(
                        link.getId(),
                        NOW))
                .thenReturn(0);

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "raw-token"))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessage(
                        ConfirmAccessUseCase.EXPIRED_OR_USED);

        assertThat(
                coordinator.getStatus())
                .isEqualTo(
                        UserStatus.PENDING);

        verify(events, never())
                .publishEvent(
                        any(CoordinatorActivated.class));

        verify(sessions, never())
                .save(any());
    }

    @Test
    void studentActivationRemainsSeparate() {

        link(
                "student@upc.edu.pe",
                AccessPurpose.ACTIVATION);

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "raw-token"))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessage(
                        ConfirmAccessUseCase.ACTIVATION_PENDING);

        verify(sessions, never())
                .save(any());
    }

    @Test
    void expiredLinkIsRejected() {

        AccessLink link =
                new AccessLink(
                        UUID.randomUUID(),
                        "UPC",
                        "student@upc.edu.pe",
                        AccessPurpose.LOGIN,
                        HASH,
                        NOW.minusSeconds(1),
                        null,
                        NOW.minusSeconds(600));

        when(
                accessLinks.findByTokenHash(
                        HASH))
                .thenReturn(
                        Optional.of(link));

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "raw-token"))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessage(
                        ConfirmAccessUseCase.EXPIRED_OR_USED);
    }

    private AccessLink link(
            String email,
            AccessPurpose purpose) {

        AccessLink link =
                new AccessLink(
                        UUID.randomUUID(),
                        "UPC",
                        email,
                        purpose,
                        HASH,
                        NOW.plusSeconds(600),
                        "127.0.0.1",
                        NOW.minusSeconds(60));

        when(
                accessLinks.findByTokenHash(
                        HASH))
                .thenReturn(
                        Optional.of(link));

        return link;
    }
}