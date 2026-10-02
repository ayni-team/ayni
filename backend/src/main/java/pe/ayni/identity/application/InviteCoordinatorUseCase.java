package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

/**
 * Invites a coordinator to an active university.
 *
 * <p>The coordinator exists as PENDING until the emailed single-use link is opened.
 */
@Service
public class InviteCoordinatorUseCase {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final AccessLinkRepository accessLinks;
    private final AccessTokenGenerator tokens;
    private final AccessLinkUrlBuilder links;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration linkTtl;

    InviteCoordinatorUseCase(
            TenantRepository tenants,
            UserRepository users,
            AccessLinkRepository accessLinks,
            AccessTokenGenerator tokens,
            AccessLinkUrlBuilder links,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${ayni.identity.access-link-ttl:PT10M}")
            Duration linkTtl) {

        this.tenants = tenants;
        this.users = users;
        this.accessLinks = accessLinks;
        this.tokens = tokens;
        this.links = links;
        this.events = events;
        this.clock = clock;
        this.linkTtl = linkTtl;
    }

    @Transactional
    public void execute(
            String tenantCode,
            String email,
            String fullName,
            String requestedIp) {

        String normalizedCode =
                normalizeCode(tenantCode);

        String normalizedEmail =
                normalizeEmail(email);

        String normalizedName =
                requireText(
                        fullName,
                        "Coordinator name");

        Tenant tenant =
                tenants.findByCodeAndStatus(
                                normalizedCode,
                                TenantStatus.ACTIVE)
                        .orElseThrow(
                                () ->
                                        new IdentityRuleViolation(
                                                "The university does not exist or is not active"));

        if (!tenant.claims(normalizedEmail)) {
            throw new IdentityRuleViolation(
                    "The coordinator email does not belong to this university");
        }

        if (users.existsByTenantIdAndEmailIgnoreCase(
                normalizedCode,
                normalizedEmail)) {

            throw new IdentityRuleViolation(
                    "A user with this email already exists in the university");
        }

        Instant now =
                clock.instant();

        User coordinator =
                new User(
                        UUID.randomUUID(),
                        normalizedCode,
                        UserRole.COORDINATOR,
                        normalizedEmail,
                        null,
                        normalizedName,
                        null,
                        null,
                        null,
                        null,
                        UserStatus.PENDING,
                        null,
                        null,
                        now,
                        now);

        users.save(coordinator);

        GeneratedAccessToken token =
                tokens.generate();

        Instant expiresAt =
                now.plus(linkTtl);

        AccessLink accessLink =
                new AccessLink(
                        UUID.randomUUID(),
                        normalizedCode,
                        normalizedEmail,
                        AccessPurpose.COORDINATOR_INVITE,
                        token.tokenHash(),
                        expiresAt,
                        requestedIp,
                        now);

        accessLinks.save(accessLink);

        events.publishEvent(
                new AccessRequested(
                        normalizedCode,
                        normalizedEmail,
                        AccessPurpose.COORDINATOR_INVITE.name(),
                        links.build(token.rawToken()),
                        expiresAt,
                        now));
    }

    private String normalizeCode(String value) {
        return requireText(
                value,
                "University code")
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        return Objects.requireNonNull(
                        value,
                        "email must not be null")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String requireText(
            String value,
            String field) {

        if (value == null
                || value.isBlank()) {

            throw new IdentityRuleViolation(
                    field + " must not be blank");
        }

        return value.trim();
    }
}