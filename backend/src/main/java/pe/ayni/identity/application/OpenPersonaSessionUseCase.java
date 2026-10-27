package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.AccessRole;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Opens a session for an active student or coordinator without the email that would carry the
 * link, so the presenter of a demonstration can change person in one request.
 *
 * <p>Only under the {@code demo} profile, and only the platform administrator reaches it (the route
 * is under {@code /api/v1/admin}). The session is the one confirming a link opens: the same token
 * generator, whose raw value travels only in the answer while the table keeps its hash, the same
 * duration {@code ayni.identity.session-ttl}, the same clock. No access link is consumed or
 * created and no notice is written. The log line names the university and the role, never the
 * address.
 */
@Service
@Profile("demo")
public class OpenPersonaSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(OpenPersonaSessionUseCase.class);

    static final String NOT_FOUND = "No active student or coordinator has that email";

    private final IdentityApi identity;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final AccessTokenGenerator tokens;
    private final Clock clock;
    private final Duration sessionTtl;

    OpenPersonaSessionUseCase(
            IdentityApi identity,
            UserRepository users,
            UserSessionRepository sessions,
            AccessTokenGenerator tokens,
            Clock clock,
            @Value("${ayni.identity.session-ttl:P7D}") Duration sessionTtl) {

        this.identity = identity;
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
    }

    /**
     * @throws NoSuchElementException when no active student or coordinator of any university has
     *     that email
     */
    @Transactional
    public ConfirmAccessResult execute(String email) {

        // The administrator has no university: look in each active one, as that one.
        for (String tenantCode : identity.activeTenantCodes()) {
            Optional<ConfirmAccessResult> opened =
                    TenantContext.callAs(
                            tenantCode,
                            () ->
                                    users.findByTenantIdAndEmailIgnoreCase(tenantCode, email)
                                            .filter(User::isActive)
                                            .map(person -> openSession(tenantCode, person)));

            if (opened.isPresent()) {
                return opened.get();
            }
        }

        throw new NoSuchElementException(NOT_FOUND);
    }

    private ConfirmAccessResult openSession(String tenantCode, User person) {

        Instant now = clock.instant();
        GeneratedAccessToken sessionToken = tokens.generate();
        Instant expiresAt = now.plus(sessionTtl);

        sessions.save(
                new UserSession(
                        UUID.randomUUID(),
                        tenantCode,
                        person.getId(),
                        sessionToken.tokenHash(),
                        expiresAt,
                        now));

        log.info(
                "A demonstration session was opened for a {} of {}",
                person.getRole(),
                tenantCode);

        return new ConfirmAccessResult(
                sessionToken.rawToken(),
                tenantCode,
                person.getId(),
                person.getRole() == UserRole.COORDINATOR
                        ? AccessRole.COORDINATOR
                        : AccessRole.STUDENT,
                expiresAt);
    }
}
