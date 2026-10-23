package pe.ayni.identity.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.CreditPolicy;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * An active coordinator of the demonstration university, so a presentation can show what a
 * coordinator sees without inviting one first.
 *
 * <p>Outside this profile a coordinator only exists after the platform administrator invites them
 * by email and they open the link (TS10). Here one is already active: they sign in by link like
 * everybody else, with the address of {@code ayni.identity.demo-coordinator-email}, which has to
 * belong to a domain of UPC. The recognition rule they look after is the one of {@code
 * DemoRecognitionData}.
 *
 * <p>Only under the {@code demo} profile, and only when the application is ready, after {@code
 * DemoIdentityData} has created the university. It runs once: a second start finds the address
 * already taken and leaves it alone. The address is never written to the log.
 */
@Component
@Profile("demo")
class DemoCoordinatorData {

    private static final Logger log = LoggerFactory.getLogger(DemoCoordinatorData.class);

    private static final String TENANT = "UPC";

    static final UUID COORDINATOR = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private final TenantRepository tenants;
    private final UserRepository users;
    private final CreditPolicyRepository creditPolicies;
    private final Clock clock;
    private final String email;

    DemoCoordinatorData(
            TenantRepository tenants,
            UserRepository users,
            CreditPolicyRepository creditPolicies,
            Clock clock,
            @Value("${ayni.identity.demo-coordinator-email:coordinator@upc.edu.pe}") String email) {

        this.tenants = tenants;
        this.users = users;
        this.creditPolicies = creditPolicies;
        this.clock = clock;
        this.email = email;
    }

    @EventListener
    void anActiveCoordinator(ApplicationReadyEvent ready) {
        try {
            // Row level security (TS01): the coordinator is written, and looked for, as UPC's.
            TenantContext.runAs(TENANT, this::fill);
        } catch (RuntimeException failure) {
            // Demonstration data must not keep the application from starting.
            log.error("Could not create the demo coordinator of {}", TENANT, failure);
        }
    }

    private void fill() {
        Tenant tenant = tenants.findByCode(TENANT).orElse(null);
        if (tenant == null) {
            log.warn("No demo coordinator: the university {} does not exist", TENANT);
            return;
        }
        ensureBaselinePolicy(tenant);
        if (!tenant.claims(email)) {
            log.warn(
                    "No demo coordinator: ayni.identity.demo-coordinator-email is not of a domain"
                            + " of {}",
                    TENANT);
            return;
        }
        if (users.findByTenantIdAndId(TENANT, COORDINATOR).isPresent()
                || users.existsByTenantIdAndEmailIgnoreCase(TENANT, email)) {
            return;
        }

        Instant now = clock.instant();
        users.save(
                new User(
                        COORDINATOR,
                        TENANT,
                        UserRole.COORDINATOR,
                        email,
                        null,
                        "Carlos Rivera",
                        null,
                        null,
                        null,
                        null,
                        UserStatus.ACTIVE,
                        null,
                        now,
                        now,
                        now));
        log.info("Demo coordinator ready in {}: {}", TENANT, COORDINATOR);
    }

    /**
     * The demonstration university is written by {@code DemoIdentityData} without a credit policy,
     * so a coordinator could neither read nor change it. A university registered over HTTP always
     * has one; this gives the seeded one the same five credits for thirty days.
     */
    private void ensureBaselinePolicy(Tenant tenant) {
        if (creditPolicies
                .findByTenantIdAndKindAndSupersededAtIsNull(TENANT, PolicyKind.BASELINE)
                .isPresent()) {
            return;
        }
        Instant now = clock.instant();
        creditPolicies.save(
                new CreditPolicy(
                        UUID.fromString("55555555-5555-4555-8555-555555555555"),
                        TENANT,
                        PolicyKind.BASELINE,
                        5,
                        30,
                        LocalDate.ofInstant(now, tenant.getTimezone()),
                        DemoPlatformAdminData.ADMIN,
                        null,
                        now));
    }
}
