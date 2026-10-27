package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.EmailDomains;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.UniversityAlreadyRegistered;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.shared.events.UniversityIdentityChanged;

/**
 * US50: the platform administrator replaces the whole list of institutional email domains of a
 * university.
 *
 * <p>A domain belongs to one university, active or not: it is how an address is routed before the
 * person has an account.
 */
@Service
public class ReplaceEmailDomainsUseCase {

    private final TenantRepository tenants;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    ReplaceEmailDomainsUseCase(
            TenantRepository tenants, Clock clock, ApplicationEventPublisher events) {
        this.tenants = tenants;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public UniversityEmailDomainsView execute(UUID administratorId, String code, List<String> emailDomains) {
        List<String> domains = EmailDomains.normalize(emailDomains);

        // The lock of registering a university: the domains of every university are read after it,
        // so two requests for the same domain, whether to two universities or to a new one, end with
        // one answer and one refusal. Without it both find the domain free and both keep it.
        tenants.lockUniversityRegistration();

        Tenant tenant =
                tenants.findByCode(code.trim().toUpperCase(Locale.ROOT))
                        .orElseThrow(() -> new NoSuchElementException("The university does not exist"));

        boolean heldByAnother =
                tenants.findAll().stream()
                        .filter(other -> !other.getId().equals(tenant.getId()))
                        .anyMatch(other -> other.holdsAnyOf(domains));

        if (heldByAnother) {
            throw UniversityAlreadyRegistered.emailDomain();
        }

        Instant now = clock.instant();
        tenant.replaceEmailDomains(domains, now);
        tenants.save(tenant);

        // The administrator's session has no university, so the university of the change is the
        // one that was edited. The event says that it changed and who did it, not the domains.
        events.publishEvent(new UniversityIdentityChanged(tenant.getCode(), administratorId, now));

        return UniversityEmailDomainsView.from(tenant);
    }
}
