package pe.ayni.identity.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * TS03: closing a session before it expires.
 *
 * <p>An opaque token is revoked by marking one row (ADR 0007), and from that moment the token
 * opens nothing: the next request that presents it is answered 401. Signing out again changes
 * nothing, so it is safe to repeat.
 */
@Service
public class SignOutUseCase {

    private final UserSessionRepository sessions;
    private final AccessTokenGenerator tokens;
    private final Clock clock;

    SignOutUseCase(UserSessionRepository sessions, AccessTokenGenerator tokens, Clock clock) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public void execute(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessions.revoke(tokens.hash(rawToken), clock.instant());
    }
}
