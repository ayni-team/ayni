package pe.ayni.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.identity.domain.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByTenantIdAndEmailIgnoreCase(
            String tenantId,
            String email);

    Optional<User> findByTenantIdAndId(
            String tenantId,
            UUID id);
}