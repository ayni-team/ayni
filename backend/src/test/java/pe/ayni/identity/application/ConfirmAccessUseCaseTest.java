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
import org.junit.jupiter.api.DisplayName;
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

/** US38's confirmation without Spring: what it reads, what it refuses and what it writes. */
class ConfirmAccessUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-09-24T15:00:00Z");

    private static final String LINK_HASH = "a".repeat(64);

    private final UUID userId = UUID.randomUUID();

    private AccessLinkRepository accessLinks;
    private UserRepository users;
    private UserSessionRepository sessions;
    private AccessTokenGenerator tokens;
    private ConfirmAccessUseCase useCase;

    @BeforeEach
    void setUp() {
        accessLinks = mock(AccessLinkRepository.class);
        users = mock(UserRepository.class);
        sessions = mock(UserSessionRepository.class);
        tokens = mock(AccessTokenGenerator.class);

        when(tokens.hash("raw-access-token"))
                .thenReturn(LINK_HASH);

        when(tokens.generate())
                .thenReturn(
                        new GeneratedAccessToken(
                                "raw-session-token",
                                "b".repeat(64)));

        useCase =
                new ConfirmAccessUseCase(
                        accessLinks,
                        users,
                        sessions,
                        tokens,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofDays(7));
    }

    private AccessLink link(String tenantId, AccessPurpose purpose, Instant expiresAt) {
        AccessLink link =
                new AccessLink(
                        UUID.randomUUID(),
                        tenantId,
                        "student@upc.edu.pe",
                        purpose,
                        LINK_HASH,
                        expiresAt,
                        "127.0.0.1",
                        NOW.minusSeconds(60));

        when(accessLinks.findByTokenHash(LINK_HASH))
                .thenReturn(Optional.of(link));

        return link;
    }

    private AccessLink loginLink() {
        return link("UPC", AccessPurpose.LOGIN, NOW.plusSeconds(600));
    }

    private void aStudent(UserStatus status) {
        when(users.findByTenantIdAndEmailIgnoreCase("UPC", "student@upc.edu.pe"))
                .thenReturn(
                        Optional.of(
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
                                        status,
                                        null,
                                        NOW.minusSeconds(3600),
                                        NOW.minusSeconds(3600),
                                        NOW.minusSeconds(3600))));
    }

    private void assertRefused(String message) {
        assertThatThrownBy(() -> useCase.execute("raw-access-token"))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessage(message);

        verify(sessions, never()).save(any());
    }

    @Test
    @DisplayName("a sign in link opens a session in the university written on the link")
    void confirmsLoginAndCreatesSession() {
        AccessLink link = loginLink();
        aStudent(UserStatus.ACTIVE);
        when(accessLinks.consume(link.getId(), NOW))
                .thenReturn(1);

        ConfirmAccessResult result =
                useCase.execute("raw-access-token");

        assertThat(result.sessionToken())
                .isEqualTo("raw-session-token");

        assertThat(result.tenantId())
                .isEqualTo("UPC");

        assertThat(result.userId())
                .isEqualTo(userId);

        assertThat(result.expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));

        ArgumentCaptor<UserSession> captor =
                ArgumentCaptor.forClass(UserSession.class);

        verify(sessions).save(captor.capture());

        assertThat(captor.getValue().getTenantId())
                .isEqualTo("UPC");

        assertThat(captor.getValue().getUserId())
                .isEqualTo(userId);

        // Only the hash of the session token is kept.
        assertThat(captor.getValue().getTokenHash())
                .isEqualTo("b".repeat(64));
    }

    @Test
    @DisplayName("a link another confirmation consumed first opens nothing")
    void aLinkConsumedMeanwhileOpensNothing() {
        AccessLink link = loginLink();
        aStudent(UserStatus.ACTIVE);
        // Read as unused, but the conditional update finds it already consumed.
        when(accessLinks.consume(link.getId(), NOW))
                .thenReturn(0);

        assertRefused(ConfirmAccessUseCase.EXPIRED_OR_USED);
    }

    @Test
    @DisplayName("an unknown token is refused")
    void anUnknownTokenIsRefused() {
        when(accessLinks.findByTokenHash(LINK_HASH))
                .thenReturn(Optional.empty());

        assertRefused(ConfirmAccessUseCase.INVALID);
    }

    @Test
    @DisplayName("an expired link is refused before anything is written")
    void anExpiredLinkIsRefused() {
        AccessLink link = link("UPC", AccessPurpose.LOGIN, NOW.minusSeconds(1));

        assertRefused(ConfirmAccessUseCase.EXPIRED_OR_USED);

        verify(accessLinks, never()).consume(link.getId(), NOW);
    }

    @Test
    @DisplayName("an activation link is refused saying creating accounts is not available yet")
    void activationRemainsPending() {
        AccessLink link = link("UPC", AccessPurpose.ACTIVATION, NOW.plusSeconds(600));

        assertRefused(ConfirmAccessUseCase.ACTIVATION_PENDING);

        // The link stays usable for when activation exists.
        verify(accessLinks, never()).consume(link.getId(), NOW);
    }

    @Test
    @DisplayName("a coordinator invitation is refused saying it is not available yet")
    void invitationRemainsPending() {
        link("UPC", AccessPurpose.COORDINATOR_INVITE, NOW.plusSeconds(600));

        assertRefused(ConfirmAccessUseCase.INVITATION_PENDING);
    }

    @Test
    @DisplayName("a link without a university is refused")
    void aLinkWithoutUniversityIsRefused() {
        link(null, AccessPurpose.LOGIN, NOW.plusSeconds(600));

        assertRefused(ConfirmAccessUseCase.NO_UNIVERSITY);
    }

    @Test
    @DisplayName("an account that is not active cannot sign in")
    void anInactiveAccountIsRefused() {
        AccessLink link = loginLink();
        aStudent(UserStatus.RESTRICTED);

        assertRefused(ConfirmAccessUseCase.NOT_ACTIVE);

        verify(accessLinks, never()).consume(link.getId(), NOW);
    }

    @Test
    @DisplayName("a blank token is refused")
    void aBlankTokenIsRefused() {
        assertThatThrownBy(() -> useCase.execute("  "))
                .isInstanceOf(IdentityRuleViolation.class);
    }
}
