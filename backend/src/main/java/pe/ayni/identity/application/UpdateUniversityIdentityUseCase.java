package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US50: updates the visual identity of the current university.
 *
 * <p>The university comes from TenantContext. A coordinator cannot choose
 * another university in the request.
 */
@Service
public class UpdateUniversityIdentityUseCase {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final Clock clock;

    UpdateUniversityIdentityUseCase(
            TenantRepository tenants,
            UserRepository users,
            Clock clock) {

        this.tenants = tenants;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public UniversityIdentityView execute(
            UUID currentUserId,
            String logoUrl,
            String primaryColor,
            String secondaryColor) {

        String tenantId =
                TenantContext.require();

        User coordinator =
                users.findByTenantIdAndId(
                                tenantId,
                                currentUserId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "The user does not belong to this university"));

        if (coordinator.getRole()
                != UserRole.COORDINATOR
                || !coordinator.isActive()) {

            throw new IdentityRuleViolation(
                    "Only an active coordinator can update the university identity");
        }

        Tenant tenant =
                tenants.findByCode(
                                tenantId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "The university does not exist"));

        Instant now =
                clock.instant();

        tenant.updateIdentity(
                logoUrl,
                primaryColor,
                secondaryColor,
                now);

        tenants.save(tenant);

        return UniversityIdentityView.from(
                tenant);
    }
}