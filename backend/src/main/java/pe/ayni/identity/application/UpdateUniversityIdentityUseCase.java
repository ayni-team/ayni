package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.CoordinatorRequired;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.events.UniversityIdentityChanged;
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
    private final ApplicationEventPublisher events;

    UpdateUniversityIdentityUseCase(
            TenantRepository tenants,
            UserRepository users,
            Clock clock,
            ApplicationEventPublisher events) {

        this.tenants = tenants;
        this.users = users;
        this.clock = clock;
        this.events = events;
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
                                        new CoordinatorRequired(
                                                "Only an active coordinator can update the university identity"));

        if (coordinator.getRole()
                != UserRole.COORDINATOR
                || !coordinator.isActive()) {

            throw new CoordinatorRequired(
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

        // Inside the transaction: a change that is rolled back publishes nothing. The event says
        // that the identity changed, not the logo or the colours.
        events.publishEvent(
                new UniversityIdentityChanged(
                        tenantId,
                        coordinator.getId(),
                        now));

        return UniversityIdentityView.from(
                tenant);
    }
}