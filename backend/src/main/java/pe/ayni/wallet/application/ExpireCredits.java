package pe.ayni.wallet.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.CreditsExpired;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.domain.model.CreditAccount;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.LedgerReason;
import pe.ayni.wallet.domain.model.Movement;
import pe.ayni.wallet.infrastructure.CreditAccountRepository;
import pe.ayni.wallet.infrastructure.CreditLotRepository;

/**
 * Takes the credits that reached their expiry out of the wallets.
 *
 * <p>The balance already ignores them: it only counts groups that are still alive, so nothing is
 * wrong with a balance between the moment credits die and the moment this runs. What this adds is
 * the entry in the history, which is what tells a student that six credits expired on the fifteenth
 * rather than leaving them to notice the total is smaller.
 */
@Service
public class ExpireCredits {

  private final CreditLotRepository lots;
  private final CreditAccountRepository accounts;
  private final LedgerWriter ledger;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  ExpireCredits(
      CreditLotRepository lots,
      CreditAccountRepository accounts,
      LedgerWriter ledger,
      ApplicationEventPublisher events,
      Clock clock) {
    this.lots = lots;
    this.accounts = accounts;
    this.ledger = ledger;
    this.events = events;
    this.clock = clock;
  }

  /**
   * The universities that have something to expire right now.
   *
   * <p>The job runs outside any request, so there is no university bound to ask for, and identity
   * does not exist yet to list them. Wallet answers it from its own groups, which is enough and
   * does not reach into another module. When identity arrives this becomes {@code
   * IdentityApi.activeTenantCodes()}.
   */
  @Transactional(readOnly = true)
  public List<String> universitiesWithCreditsToExpire() {
    return lots.tenantsWithCreditsToExpire(clock.instant());
  }

  /**
   * Expires everything overdue in the university bound to the current thread.
   *
   * <p>One transaction per university: a failure while expiring one of them leaves the others
   * expired, instead of rolling back the whole night's work.
   *
   * @return how many credits were lost, for the log
   */
  @Transactional
  public Credits forCurrentUniversity() {

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    List<CreditLot> overdue = lots.lockExpired(tenantId, now);
    if (overdue.isEmpty()) {
      return Credits.ZERO;
    }

    List<Movement> movements = new ArrayList<>(overdue.size());
    Map<UUID, Credits> lostByAccount = new LinkedHashMap<>();

    for (CreditLot lot : overdue) {
      Credits lost = lot.expire();
      // No reference: nothing outside wallet caused this, the calendar did.
      movements.add(Movement.debit(lot, lost, LedgerReason.EXPIRY, null, null));
      lostByAccount.merge(lot.accountId(), lost, Credits::plus);
    }
    ledger.append(movements);

    announce(tenantId, lostByAccount, now);

    return lostByAccount.values().stream().reduce(Credits.ZERO, Credits::plus);
  }

  /** Tells whoever is interested, once per student rather than once per group. */
  private void announce(String tenantId, Map<UUID, Credits> lostByAccount, Instant now) {
    for (CreditAccount account :
        accounts.findByTenantIdAndIdIn(tenantId, lostByAccount.keySet())) {
      events.publishEvent(
          new CreditsExpired(tenantId, account.userId(), lostByAccount.get(account.id()), now));
    }
  }
}
