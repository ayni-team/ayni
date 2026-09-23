package pe.ayni.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;

/**
 * Universities registered in Ayni.
 *
 * <p>Tenants are not tenant-scoped themselves: they define the universities available in the
 * platform.
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByCode(String code);

    Optional<Tenant> findByCodeAndStatus(String code, TenantStatus status);

    List<Tenant> findByStatus(TenantStatus status);
}