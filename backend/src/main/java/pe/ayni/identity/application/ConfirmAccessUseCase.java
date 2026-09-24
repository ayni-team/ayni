package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.AccessPurpose;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

@Service
public class ConfirmAccessUseCase {

    private final AccessLinkRepository accessLinks;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final AccessTokenGenerator tokens;
    private final Clock clock;
    private final Duration sessionTtl;

    public ConfirmAccessUseCase(
            AccessLinkRepository accessLinks,
            UserRepository users,
            UserSessionRepository sessions,
            AccessTokenGenerator tokens,
            Clock clock,
            @Value("${ayni.identity.session-ttl:P7D}") Duration sessionTtl) {

        this.accessLinks = accessLinks;
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
    }

    @Transactional
    public ConfirmAccessResult execute(
            String tenantId,
            String rawToken) {

        if (tenantId == null || tenantId.isBlank()) {
            throw new IdentityRuleViolation("Tenant must not be blank");
        }

        if (rawToken == null || rawToken.isBlank()) {
            throw new IdentityRuleViolation("Access token must not be blank");
        }

        Instant now = clock.instant();
        String tokenHash = tokens.hash(rawToken);

        AccessLink accessLink =
                accessLinks
                        .findByTenantIdAndTokenHash(tenantId, tokenHash)
                        .orElseThrow(
                                () ->
                                        new IdentityRuleViolation(
                                                "Access link is invalid"));

        if (!accessLink.isUsable(now)) {
            throw new IdentityRuleViolation(
                    "Access link is expired or already consumed");
        }

        if (accessLink.getPurpose() != AccessPurpose.LOGIN) {
            throw new IdentityRuleViolation(
                    "Activation confirmation is pending academic identity provisioning");
        }

        User user =
                users
                        .findByTenantIdAndEmailIgnoreCase(
                                tenantId,
                                accessLink.getEmail())
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "User not found for access link"));

        if (!user.isActive()) {
            throw new IdentityRuleViolation(
                    "User is not active");
        }

        GeneratedAccessToken sessionToken =
                tokens.generate();

        Instant expiresAt =
                now.plus(sessionTtl);

        UserSession session =
                new UserSession(
                        UUID.randomUUID(),
                        tenantId,
                        user.getId(),
                        sessionToken.tokenHash(),
                        expiresAt,
                        now);

        sessions.save(session);

        accessLink.consume(now);

        return new ConfirmAccessResult(
                sessionToken.rawToken(),
                tenantId,
                user.getId(),
                expiresAt);
    }
}