package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.AccessRole;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.SessionResolution;
import pe.ayni.identity.domain.model.SessionResolution.Outcome;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * TS03: who a session token belongs to, and in which university.
 *
 * <p>The token is looked up by its hash, as it is stored, so the lookup is one row by index (ADR
 * 0007). A token that is unknown, expired or revoked is answered as such and nothing else is read:
 * the request it came with is refused without touching anybody's data. An open session names its
 * university and its person, and the role comes from where that person lives: {@code
 * identity.users} for a student or a coordinator, {@code identity.platform_admins} for an
 * administrator. A session whose person no longer exists, or an administrator who was disabled,
 * opens nothing either.
 *
 * <p>The status of a student or a coordinator is not looked at here. A restricted coordinator is
 * refused by the use case they call, which is the one that knows whether to write that refusal
 * down (US48).
 */
@Service
public class ResolveSessionQuery {

    private final UserSessionRepository sessions;
    private final UserRepository users;
    private final PlatformAdminRepository admins;
    private final AccessTokenGenerator tokens;
    private final Clock clock;

    ResolveSessionQuery(
            UserSessionRepository sessions,
            UserRepository users,
            PlatformAdminRepository admins,
            AccessTokenGenerator tokens,
            Clock clock) {

        this.sessions = sessions;
        this.users = users;
        this.admins = admins;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SessionResolution execute(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return SessionResolution.refused(Outcome.UNKNOWN);
        }

        Optional<UserSession> found = sessions.findByTokenHash(tokens.hash(rawToken));
        if (found.isEmpty()) {
            return SessionResolution.refused(Outcome.UNKNOWN);
        }

        UserSession session = found.get();
        Instant now = clock.instant();
        if (session.isRevoked()) {
            return SessionResolution.refused(Outcome.REVOKED);
        }
        if (session.isExpired(now)) {
            return SessionResolution.refused(Outcome.EXPIRED);
        }

        if (session.belongsToPlatformAdmin()) {
            return admins.findById(session.getAdminId())
                    .filter(PlatformAdmin::isActive)
                    .map(admin -> SessionResolution.open(null, admin.getId(), AccessRole.PLATFORM_ADMIN))
                    .orElse(SessionResolution.refused(Outcome.UNKNOWN));
        }

        // identity.users is tenant scoped (TS01): the person is read as their university's, the
        // one the session says. The sessions table is not, which is what lets it be read first.
        SessionResolution[] resolution = new SessionResolution[1];
        TenantContext.runAs(
                session.getTenantId(),
                () ->
                        resolution[0] =
                                users.findByTenantIdAndId(session.getTenantId(), session.getUserId())
                                        .map(user -> SessionResolution.open(
                                                user.getTenantId(), user.getId(), roleOf(user)))
                                        .orElse(SessionResolution.refused(Outcome.UNKNOWN)));
        return resolution[0];
    }

    private static AccessRole roleOf(User user) {
        return switch (user.getRole()) {
            case STUDENT -> AccessRole.STUDENT;
            case COORDINATOR -> AccessRole.COORDINATOR;
        };
    }
}
