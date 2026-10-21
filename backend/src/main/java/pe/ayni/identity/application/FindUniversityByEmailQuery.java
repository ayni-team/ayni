package pe.ayni.identity.application;

import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.EmailDomains;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.infrastructure.TenantRepository;

/**
 * US39: the name, logo and colours of the university an email belongs to, for somebody who has not
 * signed in yet.
 *
 * <p>Only the domain of the address is looked at, as when an access link is requested: nothing says
 * whether the address has an account, and a suspended university answers as one that is not
 * affiliated.
 */
@Service
@Transactional(readOnly = true)
public class FindUniversityByEmailQuery {

    private final TenantRepository tenants;

    FindUniversityByEmailQuery(TenantRepository tenants) {
        this.tenants = tenants;
    }

    public UniversityIdentityView execute(String email) {
        String address = EmailDomains.requireAddress(email);

        return tenants.findByStatus(TenantStatus.ACTIVE).stream()
                .filter(tenant -> tenant.claims(address))
                .findFirst()
                .map(UniversityIdentityView::from)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "The institution is not affiliated with Ayni"));
    }
}
