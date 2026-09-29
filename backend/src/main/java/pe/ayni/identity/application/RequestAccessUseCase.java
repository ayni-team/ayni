package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.AccessPurpose;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.events.AccessRequested;

@Service
public class RequestAccessUseCase {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final AccessLinkRepository accessLinks;
    private final AccessTokenGenerator tokens;
    private final AccessLinkUrlBuilder links;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration linkTtl;

    RequestAccessUseCase(
            TenantRepository tenants,
            UserRepository users,
            AccessLinkRepository accessLinks,
            AccessTokenGenerator tokens,
            AccessLinkUrlBuilder links,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${ayni.identity.access-link-ttl:PT10M}") Duration linkTtl) {

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
    public void execute(String email, String requestedIp) {
        String normalizedEmail = normalizeEmail(email);
        Tenant tenant = findTenant(normalizedEmail);

        AccessPurpose purpose =
                users.existsByTenantIdAndEmailIgnoreCase(
                        tenant.getCode(), normalizedEmail)
                        ? AccessPurpose.LOGIN
                        : AccessPurpose.ACTIVATION;

        GeneratedAccessToken token = tokens.generate();

        Instant now = clock.instant();
        Instant expiresAt = now.plus(linkTtl);

        AccessLink accessLink =
                new AccessLink(
                        UUID.randomUUID(),
                        tenant.getCode(),
                        normalizedEmail,
                        purpose,
                        token.tokenHash(),
                        expiresAt,
                        requestedIp,
                        now);

        accessLinks.save(accessLink);

        events.publishEvent(
                new AccessRequested(
                        tenant.getCode(),
                        normalizedEmail,
                        purpose.name(),
                        links.build(token.rawToken()),
                        expiresAt,
                        now));
    }

    private Tenant findTenant(String email) {
        List<Tenant> activeTenants =
                tenants.findByStatus(TenantStatus.ACTIVE);

        return activeTenants.stream()
                .filter(tenant -> tenant.claims(email))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IdentityRuleViolation(
                                        "The institution is not affiliated with Ayni"));
    }

    private String normalizeEmail(String email) {
        String normalized =
                Objects.requireNonNull(email, "email must not be null")
                        .trim()
                        .toLowerCase(Locale.ROOT);

        if (normalized.isBlank()) {
            throw new IdentityRuleViolation("Email must not be blank");
        }

        return normalized;
    }
}