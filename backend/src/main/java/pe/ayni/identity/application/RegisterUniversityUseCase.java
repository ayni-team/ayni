package pe.ayni.identity.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.domain.model.CreditPolicy;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.infrastructure.CreditPolicyRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.shared.events.UniversityRegistered;

@Service
public class RegisterUniversityUseCase {

    private final TenantRepository tenants;
    private final CreditPolicyRepository creditPolicies;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RegisterUniversityUseCase(
            TenantRepository tenants,
            CreditPolicyRepository creditPolicies,
            ApplicationEventPublisher events,
            Clock clock) {

        this.tenants = tenants;
        this.creditPolicies = creditPolicies;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UniversityAdminView execute(
            UUID administratorId,
            String code,
            String name,
            String logoUrl,
            String primaryColor,
            String secondaryColor,
            List<String> emailDomains,
            BigDecimal minimumTeachingGrade,
            String timezone,
            int initialCredits,
            int initialCreditValidityDays) {

        String normalizedCode =
                normalizeCode(code);

        List<String> normalizedDomains =
                normalizeDomains(emailDomains);

        if (tenants.findByCode(normalizedCode).isPresent()) {
            throw new IdentityRuleViolation(
                    "A university with this code already exists");
        }

        ensureDomainsAreAvailable(
                normalizedDomains);

        Instant now =
                clock.instant();

        Tenant tenant =
                new Tenant(
                        UUID.randomUUID(),
                        normalizedCode,
                        requireText(name, "University name"),
                        blankToNull(logoUrl),
                        blankToNull(primaryColor),
                        blankToNull(secondaryColor),
                        normalizedDomains,
                        minimumTeachingGrade,
                        parseTimezone(timezone),
                        TenantStatus.ACTIVE,
                        now);

        tenants.save(tenant);

        CreditPolicy initialPolicy =
                new CreditPolicy(
                        UUID.randomUUID(),
                        normalizedCode,
                        PolicyKind.BASELINE,
                        initialCredits,
                        initialCreditValidityDays,
                        LocalDate.ofInstant(
                                now,
                                tenant.getTimezone()),
                        administratorId,
                        null,
                        now);

        creditPolicies.save(
                initialPolicy);

        events.publishEvent(
                new UniversityRegistered(
                        normalizedCode,
                        tenant.getName(),
                        now));

        return toView(
                tenant,
                0,
                0);
    }

    private void ensureDomainsAreAvailable(
            List<String> domains) {

        List<Tenant> existing =
                tenants.findAll();

        boolean duplicated =
                existing.stream()
                        .flatMap(
                                tenant ->
                                        tenant.getEmailDomains()
                                                .stream())
                        .map(
                                domain ->
                                        domain.toLowerCase(
                                                Locale.ROOT))
                        .anyMatch(
                                domains::contains);

        if (duplicated) {
            throw new IdentityRuleViolation(
                    "An institutional email domain is already registered");
        }
    }

    private String normalizeCode(
            String value) {

        return requireText(
                value,
                "University code")
                .toUpperCase(
                        Locale.ROOT);
    }

    private List<String> normalizeDomains(
            List<String> values) {

        if (values == null
                || values.isEmpty()) {
            throw new IdentityRuleViolation(
                    "At least one institutional email domain is required");
        }

        List<String> normalized =
                values.stream()
                        .map(
                                value ->
                                        requireText(
                                                value,
                                                "Email domain")
                                                .toLowerCase(
                                                        Locale.ROOT))
                        .distinct()
                        .toList();

        if (normalized.isEmpty()) {
            throw new IdentityRuleViolation(
                    "At least one institutional email domain is required");
        }

        return normalized;
    }

    private ZoneId parseTimezone(
            String value) {

        try {
            return ZoneId.of(
                    requireText(
                            value,
                            "Timezone"));
        } catch (RuntimeException exception) {
            throw new IdentityRuleViolation(
                    "Timezone is not valid");
        }
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

    private String blankToNull(
            String value) {

        if (value == null
                || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private UniversityAdminView toView(
            Tenant tenant,
            long students,
            long coordinators) {

        return new UniversityAdminView(
                tenant.getCode(),
                tenant.getName(),
                tenant.getLogoUrl(),
                tenant.getPrimaryColor(),
                tenant.getSecondaryColor(),
                tenant.getEmailDomains(),
                tenant.getMinimumTeachingGrade(),
                tenant.getTimezone()
                        .getId(),
                tenant.isActive(),
                students,
                coordinators);
    }
}