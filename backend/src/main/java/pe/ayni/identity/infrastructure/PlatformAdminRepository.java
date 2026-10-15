package pe.ayni.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.identity.domain.model.PlatformAdmin;

/**
 * The platform administrators. One of the four tables ADR 0007 leaves without a university: an
 * administrator belongs to none, so it is read by email or by id and never by {@code tenant_id}.
 */
public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {

    Optional<PlatformAdmin> findByEmailIgnoreCase(String email);
}
