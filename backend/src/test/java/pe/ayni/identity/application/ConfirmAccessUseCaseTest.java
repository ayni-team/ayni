package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

class ConfirmAccessUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-09-24T15:00:00Z");

    @Test
    void confirmsLoginAndCreatesSession() {
        AccessLinkRepository accessLinks =
                mock(AccessLinkRepository.class);

        UserRepository users =
                mock(UserRepository.class);

        UserSessionRepository sessions =
                mock(UserSessionRepository.class);

        AccessTokenGenerator tokens =
                mock(AccessTokenGenerator.class);

        UUID userId = UUID.randomUUID();

        AccessLink link =
                new AccessLink(
                        UUID.randomUUID(),
                        "UPC",
                        "student@upc.edu.pe",
                        AccessPurpose.LOGIN,
                        "a".repeat(64),
                        NOW.plusSeconds(600),
                        "127.0.0.1",
                        NOW.minusSeconds(60));

        User user =
                new User(
                        userId,
                        "UPC",
                        UserRole.STUDENT,
                        "student@upc.edu.pe",
                        "U202612345",
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

        when(tokens.hash("raw-access-token"))
                .thenReturn("a".repeat(64));

        when(accessLinks.findByTenantIdAndTokenHash(
                "UPC",
                "a".repeat(64)))
                .thenReturn(Optional.of(link));

        when(users.findByTenantIdAndEmailIgnoreCase(
                "UPC",
                "student@upc.edu.pe"))
                .thenReturn(Optional.of(user));

        when(tokens.generate())
                .thenReturn(
                        new GeneratedAccessToken(
                                "raw-session-token",
                                "b".repeat(64)));

        ConfirmAccessUseCase useCase =
                new ConfirmAccessUseCase(
                        accessLinks,
                        users,
                        sessions,
                        tokens,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofDays(7));

        ConfirmAccessResult result =
                useCase.execute(
                        "UPC",
                        "raw-access-token");

        assertThat(result.sessionToken())
                .isEqualTo("raw-session-token");

        assertThat(result.userId())
                .isEqualTo(userId);

        assertThat(link.getConsumedAt())
                .isEqualTo(NOW);

        ArgumentCaptor<UserSession> captor =
                ArgumentCaptor.forClass(UserSession.class);

        verify(sessions).save(captor.capture());

        assertThat(captor.getValue().getTenantId())
                .isEqualTo("UPC");

        assertThat(captor.getValue().getUserId())
                .isEqualTo(userId);

        assertThat(captor.getValue().getTokenHash())
                .isEqualTo("b".repeat(64));
    }

    @Test
    void activationRemainsPending() {
        AccessLinkRepository accessLinks =
                mock(AccessLinkRepository.class);

        UserRepository users =
                mock(UserRepository.class);

        UserSessionRepository sessions =
                mock(UserSessionRepository.class);

        AccessTokenGenerator tokens =
                mock(AccessTokenGenerator.class);

        AccessLink link =
                new AccessLink(
                        UUID.randomUUID(),
                        "UPC",
                        "new@upc.edu.pe",
                        AccessPurpose.ACTIVATION,
                        "a".repeat(64),
                        NOW.plusSeconds(600),
                        null,
                        NOW);

        when(tokens.hash("token"))
                .thenReturn("a".repeat(64));

        when(accessLinks.findByTenantIdAndTokenHash(
                "UPC",
                "a".repeat(64)))
                .thenReturn(Optional.of(link));

        ConfirmAccessUseCase useCase =
                new ConfirmAccessUseCase(
                        accessLinks,
                        users,
                        sessions,
                        tokens,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofDays(7));

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "UPC",
                                "token"))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining(
                        "pending academic identity");
    }
}