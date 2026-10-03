package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.CoordinatorActivated;

/**
 * Confirms a single-use access link and opens a session.
 *
 * <p>The tenant always comes from the access link. The client never chooses the university during
 * confirmation.
 */
@Service
public class ConfirmAccessUseCase {

    static final String INVALID =
            "This access link is not valid. Ask for a new one";

    static final String EXPIRED_OR_USED =
            "This access link has expired or was already used. Ask for a new one";

    static final String ACTIVATION_PENDING =
            "This link would create a new Ayni student account, and student activation is not"
                    + " available in this version yet";

    static final String NO_UNIVERSITY =
            "Signing in without a university, as a platform administrator, is not available yet";

    static final String NOT_ACTIVE =
            "This account is not active";

    static final String INVALID_COORDINATOR_INVITATION =
            "This coordinator invitation is no longer valid";

    private final AccessLinkRepository accessLinks;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final AccessTokenGenerator tokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration sessionTtl;

    ConfirmAccessUseCase(
            AccessLinkRepository accessLinks,
            UserRepository users,
            UserSessionRepository sessions,
            AccessTokenGenerator tokens,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${ayni.identity.session-ttl:P7D}")
            Duration sessionTtl) {

        this.accessLinks = accessLinks;
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
    }

    @Transactional
    public ConfirmAccessResult execute(String rawToken) {

        if (rawToken == null
                || rawToken.isBlank()) {

            throw new IdentityRuleViolation(
                    "Access token must not be blank");
        }

        Instant now =
                clock.instant();

        AccessLink accessLink =
                accessLinks
                        .findByTokenHash(
                                tokens.hash(rawToken))
                        .orElseThrow(
                                () ->
                                        new IdentityRuleViolation(
                                                INVALID));

        if (!accessLink.isUsable(now)) {
            throw new IdentityRuleViolation(
                    EXPIRED_OR_USED);
        }

        String tenantId =
                accessLink.getTenantId();

        if (tenantId == null) {
            throw new IdentityRuleViolation(
                    NO_UNIVERSITY);
        }

        User user =
                switch (accessLink.getPurpose()) {

                    case LOGIN ->
                            confirmLogin(
                                    accessLink,
                                    tenantId,
                                    now);

                    case COORDINATOR_INVITE ->
                            activateCoordinator(
                                    accessLink,
                                    tenantId,
                                    now);

                    case ACTIVATION ->
                            throw new IdentityRuleViolation(
                                    ACTIVATION_PENDING);
                };

        return openSession(
                tenantId,
                user,
                now);
    }

    private User confirmLogin(
            AccessLink accessLink,
            String tenantId,
            Instant now) {

        User user =
                users.findByTenantIdAndEmailIgnoreCase(
                                tenantId,
                                accessLink.getEmail())
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "User not found for access link"));

        if (!user.isActive()) {
            throw new IdentityRuleViolation(
                    NOT_ACTIVE);
        }

        consume(
                accessLink,
                now);

        return user;
    }

    private User activateCoordinator(
            AccessLink accessLink,
            String tenantId,
            Instant now) {

        User coordinator =
                users.findByTenantIdAndEmailIgnoreCase(
                                tenantId,
                                accessLink.getEmail())
                        .orElseThrow(
                                () ->
                                        new IdentityRuleViolation(
                                                INVALID_COORDINATOR_INVITATION));

        if (coordinator.getRole()
                != UserRole.COORDINATOR
                || coordinator.getStatus()
                != UserStatus.PENDING) {

            throw new IdentityRuleViolation(
                    INVALID_COORDINATOR_INVITATION);
        }

        /*
         * Consume first. If another confirmation won the race, no user or
         * session state is changed. Everything participates in this transaction.
         */
        consume(
                accessLink,
                now);

        coordinator.activateCoordinator(
                now);

        users.saveAndFlush(
                coordinator);

        events.publishEvent(
                new CoordinatorActivated(
                        tenantId,
                        coordinator.getId(),
                        now));

        return coordinator;
    }

    private ConfirmAccessResult openSession(
            String tenantId,
            User user,
            Instant now) {

        GeneratedAccessToken sessionToken =
                tokens.generate();

        Instant expiresAt =
                now.plus(sessionTtl);

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

    private void consume(
            AccessLink accessLink,
            Instant now) {

        if (accessLinks.consume(
                accessLink.getId(),
                now)
                == 0) {

            throw new IdentityRuleViolation(
                    EXPIRED_OR_USED);
        }
    }
}