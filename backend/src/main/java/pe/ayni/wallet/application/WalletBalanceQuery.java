package pe.ayni.wallet.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.domain.model.Balance;
import pe.ayni.wallet.infrastructure.CreditLotRepository;

/**
 * Answers what a student holds.
 *
 * <p>The balance is computed from the groups on every read, and the read is what decides which of
 * them are still alive, so a group that expired a minute ago stops counting without anything having
 * run in between.
 */
@Service
class WalletBalanceQuery {

  private final Accounts accounts;
  private final CreditLotRepository lots;
  private final Clock clock;

  WalletBalanceQuery(Accounts accounts, CreditLotRepository lots, Clock clock) {
    this.accounts = accounts;
    this.lots = lots;
    this.clock = clock;
  }

  /**
   * The balance of a student, broken down by where the credits came from.
   *
   * <p>A student nobody has granted anything to has an empty wallet, not a missing one: they are
   * shown a balance of zero and how to obtain credits, which is what US23 asks for.
   */
  @Transactional(readOnly = true)
  Balance of(UUID userId) {
    String tenantId = TenantContext.require();
    return accounts
        .find(userId)
        .map(account -> lots.findByTenantIdAndAccountId(tenantId, account.id()))
        .map(groups -> Balance.from(groups, clock.instant()))
        .orElseGet(Balance::empty);
  }

  /**
   * Every credit the student ever earned by teaching, spent or not.
   *
   * <p>Recognition asks for this rather than for the balance: what backs a request is the teaching
   * that happened, and spending those credits afterwards does not undo it.
   */
  @Transactional(readOnly = true)
  Credits earnedTotal(UUID userId) {
    String tenantId = TenantContext.require();
    return accounts
        .find(userId)
        .map(account -> lots.sumOriginalAmount(tenantId, account.id(), CreditType.EARNED))
        .map(total -> Credits.of(Math.toIntExact(total)))
        .orElse(Credits.ZERO);
  }
}
