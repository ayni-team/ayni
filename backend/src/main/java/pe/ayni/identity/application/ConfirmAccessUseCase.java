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
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * US38: opening the link that arrived by email signs the student in.
 *
 * <p>The link is found by the hash of its token alone, and the university is the one written on
 * the link when it was requested. The client does not say which university it belongs to: a client
 * that could say so could claim any of them, which is the hole the second iteration closes.
 *
 * <p>A link works once. Consuming it is a single conditional update, so two confirmations of the
 * same link at the same moment open one session, not two. It is consumed before the session is
 * written and in the same transaction, so a confirmation that fails after consuming it leaves the
 * link as it was.
 *
 * <p>Only signing in exists today. A link that would activate a new student, or accept a
 * coordinator's invitation, is refused with a message that says so: creating those accounts needs
 * the academic system and is a story of its own.
 */
@Service
public class ConfirmAccessUseCase {

    static final String INVALID = "This access link is not valid. Ask for a new one";
    static final String EXPIRED_OR_USED =
            "This access link has expired or was already used. Ask for a new one";
    static final String ACTIVATION_PENDING =
            "This link would create a new Ayni account, and creating accounts is not available"
                    + " yet. Only students who already have an account can sign in for now";
    static final String INVITATION_PENDING =
            "This link accepts a coordinator invitation, which is not available yet";
    static final String NO_UNIVERSITY =
            "Signing in without a university, as a platform administrator, is not available yet";
    static final String NOT_ACTIVE = "This account is not active";

    private final AccessLinkRepository accessLinks;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final AccessTokenGenerator tokens;
    private final Clock clock;
    private final Duration sessionTtl;

    ConfirmAccessUseCase(
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

    /**
     * @param rawToken the token of the link, as the email carried it
     * @return the session opened, with its token in clear: the only time anybody sees it
     * @throws IdentityRuleViolation when the link does not exist, has expired, was already used, or
     *     is not a sign in link, or the account is not active
     */
    @Transactional
    public ConfirmAccessResult execute(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new IdentityRuleViolation("Access token must not be blank");
        }

        Instant now = clock.instant();

        AccessLink accessLink =
                accessLinks
                        .findByTokenHash(tokens.hash(rawToken))
                        .orElseThrow(() -> new IdentityRuleViolation(INVALID));

        if (!accessLink.isUsable(now)) {
            throw new IdentityRuleViolation(EXPIRED_OR_USED);
        }

        switch (accessLink.getPurpose()) {
            case ACTIVATION -> throw new IdentityRuleViolation(ACTIVATION_PENDING);
            case COORDINATOR_INVITE -> throw new IdentityRuleViolation(INVITATION_PENDING);
            case LOGIN -> {
                // Signing in is what this use case does.
            }
        }

        String tenantId = accessLink.getTenantId();
        if (tenantId == null) {
            throw new IdentityRuleViolation(NO_UNIVERSITY);
        }

        User user =
                users.findByTenantIdAndEmailIgnoreCase(tenantId, accessLink.getEmail())
                        .orElseThrow(
                                () -> new NoSuchElementException("User not found for access link"));

        if (!user.isActive()) {
            throw new IdentityRuleViolation(NOT_ACTIVE);
        }

        // Everything above only read. This is where two confirmations of the same link part ways:
        // the database lets exactly one of them consume it.
        if (accessLinks.consume(accessLink.getId(), now) == 0) {
            throw new IdentityRuleViolation(EXPIRED_OR_USED);
        }

        GeneratedAccessToken sessionToken = tokens.generate();
        Instant expiresAt = now.plus(sessionTtl);

        sessions.save(
                new UserSession(
                        UUID.randomUUID(),
                        tenantId,
                        user.getId(),
                        sessionToken.tokenHash(),
                        expiresAt,
                        now));

        return new ConfirmAccessResult(
                sessionToken.rawToken(),
                tenantId,
                user.getId(),
                expiresAt);
    }
}
