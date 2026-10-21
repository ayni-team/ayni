package pe.ayni.identity.application;

import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US39: the university of the person who asks, as the session binds it. The request does not name
 * one.
 */
@Service
@Transactional(readOnly = true)
public class GetMyUniversityQuery {

    private final TenantRepository tenants;

    GetMyUniversityQuery(TenantRepository tenants) {
        this.tenants = tenants;
    }

    public MyUniversityView execute() {
        String tenantId = TenantContext.require();

        return tenants.findByCode(tenantId)
                .map(MyUniversityView::from)
                .orElseThrow(() -> new NoSuchElementException("The university does not exist"));
    }
}
