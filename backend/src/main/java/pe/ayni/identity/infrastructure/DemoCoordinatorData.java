package pe.ayni.identity.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.CreditPolicy;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.shared.demo.DemoCast;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * An active coordinator of each demonstration university, so a presentation can show what a
 * coordinator sees without inviting one first.
 *
 * <p>Outside this profile a coordinator only exists after the platform administrator invites them
 * by email and they open the link (TS10). Here one per university is already active: they sign in
 * by link like everybody else. The coordinator of UPC, Carlos Rivera, has the address of {@code
 * ayni.identity.demo-coordinator-email}; the one of USIL, Rosa Huamán, the address of {@code
 * ayni.identity.demo-usil-coordinator-email}. Each address has to belong to a domain of its
 * university. The recognition rule the one of UPC looks after is the one of {@code
 * DemoRecognitionData}.
 *
 * <p>Only under the {@code demo} profile, and only when the application is ready, after {@code
 * DemoIdentityData} has registered the universities. It runs once: a second start finds the
 * addresses already taken and leaves them alone. An address is never written to the log.
 */
@Component
@Profile("demo")
class DemoCoordinatorData {

    private static final Logger log = LoggerFactory.getLogger(DemoCoordinatorData.class);

    static final UUID COORDINATOR = DemoCast.UPC_COORDINATOR;

    static final UUID USIL_COORDINATOR = DemoCast.USIL_COORDINATOR;

    /** A coordinator of one university, and the baseline policy it keeps if it has none. */
    private record Coordinator(String tenant, UUID id, String fullName, String email, UUID policy) {}

    private final TenantRepository tenants;
    private final UserRepository users;
    private final CreditPolicyRepository creditPolicies;
    private final Clock clock;
    private final List<Coordinator> coordinators;

    DemoCoordinatorData(
            TenantRepository tenants,
            UserRepository users,
            CreditPolicyRepository creditPolicies,
            Clock clock,
            @Value("${ayni.identity.demo-coordinator-email:coordinator@upc.edu.pe}") String email,
            @Value("${ayni.identity.demo-usil-coordinator-email:coordinator@usil.edu.pe}")
                    String usilEmail) {

        this.tenants = tenants;
        this.users = users;
        this.creditPolicies = creditPolicies;
        this.clock = clock;
        this.coordinators =
                List.of(
                        new Coordinator(
                                "UPC",
                                COORDINATOR,
                                "Carlos Rivera",
                                email,
                                UUID.fromString("55555555-5555-4555-8555-555555555555")),
                        new Coordinator(
                                "USIL",
                                USIL_COORDINATOR,
                                "Rosa Huamán",
                                usilEmail,
                                UUID.fromString("55555555-5555-4555-8555-555555555556")));
    }

    // Before the history of the demo profile, where a coordinator decides a request.
    @EventListener
    @Order(10)
    void anActiveCoordinator(ApplicationReadyEvent ready) {
        for (Coordinator coordinator : coordinators) {
            try {
                // Row level security (TS01): each one is written, and looked for, as their
                // university's.
                TenantContext.runAs(coordinator.tenant(), () -> fill(coordinator));
            } catch (RuntimeException failure) {
                // Demonstration data must not keep the application from starting.
                log.error("Could not create the demo coordinator of {}", coordinator.tenant(), failure);
            }
        }
    }

    private void fill(Coordinator coordinator) {
        String tenantCode = coordinator.tenant();
        Tenant tenant = tenants.findByCode(tenantCode).orElse(null);
        if (tenant == null) {
            log.warn("No demo coordinator: the university {} does not exist", tenantCode);
            return;
        }
        ensureBaselinePolicy(tenant, coordinator.policy());
        if (!tenant.claims(coordinator.email())) {
            log.warn(
                    "No demo coordinator: the configured address is not of a domain of {}",
                    tenantCode);
            return;
        }
        if (users.findByTenantIdAndId(tenantCode, coordinator.id()).isPresent()
                || users.existsByTenantIdAndEmailIgnoreCase(tenantCode, coordinator.email())) {
            return;
        }

        Instant now = clock.instant();
        users.save(
                new User(
                        coordinator.id(),
                        tenantCode,
                        UserRole.COORDINATOR,
                        coordinator.email(),
                        null,
                        coordinator.fullName(),
                        null,
                        null,
                        null,
                        null,
                        UserStatus.ACTIVE,
                        null,
                        now,
                        now,
                        now));
        log.info("Demo coordinator ready in {}: {}", tenantCode, coordinator.id());
    }

    /**
     * A university registered by {@code DemoIdentityData} has the baseline policy every
     * registration creates. A database seeded before the academic documents has a UPC written
     * without one, so a coordinator could neither read nor change it; this gives it the same five
     * credits for thirty days.
     */
    private void ensureBaselinePolicy(Tenant tenant, UUID policy) {
        if (creditPolicies
                .findByTenantIdAndKindAndSupersededAtIsNull(tenant.getCode(), PolicyKind.BASELINE)
                .isPresent()) {
            return;
        }
        Instant now = clock.instant();
        creditPolicies.save(
                new CreditPolicy(
                        policy,
                        tenant.getCode(),
                        PolicyKind.BASELINE,
                        5,
                        30,
                        LocalDate.ofInstant(now, tenant.getTimezone()),
                        DemoPlatformAdminData.ADMIN,
                        null,
                        now));
    }
}
