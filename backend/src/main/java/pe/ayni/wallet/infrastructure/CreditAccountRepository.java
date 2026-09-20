package pe.ayni.wallet.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.wallet.domain.model.CreditAccount;

/**
 * Credit accounts.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface CreditAccountRepository extends JpaRepository<CreditAccount, UUID> {

  Optional<CreditAccount> findByTenantIdAndUserId(String tenantId, UUID userId);

  boolean existsByTenantIdAndUserId(String tenantId, UUID userId);
}
