package pe.ayni.wallet.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.domain.model.CreditAccount;
import pe.ayni.wallet.infrastructure.CreditAccountRepository;

/**
 * Finds the credit account a use case is about to work on.
 *
 * <p>The university always comes from {@link TenantContext}, never from the caller: a use case that
 * accepted a tenant as a parameter would be one refactoring away from reading another university's
 * wallet.
 */
@Component
class Accounts {

  private final CreditAccountRepository accounts;
  private final Clock clock;

  Accounts(CreditAccountRepository accounts, Clock clock) {
    this.accounts = accounts;
    this.clock = clock;
  }

  /** The account of a student, when they have one. */
  Optional<CreditAccount> find(UUID userId) {
    return accounts.findByTenantIdAndUserId(TenantContext.require(), userId);
  }

  /**
   * The account of a student, opened if this is the first time credits reach them.
   *
   * <p>Accounts are not created when a student joins, because nothing would distinguish an empty
   * wallet from a wallet that does not exist. They appear the first time something is granted.
   */
  CreditAccount openIfAbsent(UUID userId) {
    String tenantId = TenantContext.require();
    return accounts
        .findByTenantIdAndUserId(tenantId, userId)
        .orElseGet(() -> accounts.save(CreditAccount.open(tenantId, userId, clock.instant())));
  }
}
