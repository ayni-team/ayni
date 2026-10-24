package pe.ayni.identity.application;

import java.util.NoSuchElementException;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.EmailDomains;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.infrastructure.TenantRepository;

/**
 * US39: the name, logo and colours of the university an email belongs to, for somebody who has not
 * signed in yet.
 *
 * <p>Only the domain of the address is looked at, as when an access link is requested: nothing says
 * whether the address has an account, and a suspended university answers as one that is not
 * affiliated. The domain can also be sent alone, which is how the screens ask (W24).
 */
@Service
@Transactional(readOnly = true)
public class FindUniversityByEmailQuery {

    private final TenantRepository tenants;

    FindUniversityByEmailQuery(TenantRepository tenants) {
        this.tenants = tenants;
    }

    /** The university that claims the domain of this address. */
    public UniversityIdentityView execute(String email) {
        String address = EmailDomains.requireAddress(email);

        return find(tenant -> tenant.claims(address));
    }

    /**
     * The university that claims this domain, for a caller that sends only the domain so the
     * address of a person never travels.
     */
    public UniversityIdentityView executeForDomain(String domain) {
        String name = EmailDomains.requireLookupDomain(domain);

        return find(tenant -> tenant.claimsDomain(name));
    }

    private UniversityIdentityView find(Predicate<Tenant> claims) {
        return tenants.findByStatus(TenantStatus.ACTIVE).stream()
                .filter(claims)
                .findFirst()
                .map(UniversityIdentityView::from)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "The institution is not affiliated with Ayni"));
    }
}
