package pe.ayni.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.domain.model.CreditPolicy;

public interface CreditPolicyRepository
        extends JpaRepository<CreditPolicy, UUID> {

    Optional<CreditPolicy>
    findByTenantIdAndKindAndSupersededAtIsNull(
            String tenantId,
            PolicyKind kind);
}