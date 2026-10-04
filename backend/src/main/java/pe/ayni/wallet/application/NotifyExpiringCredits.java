package pe.ayni.wallet.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.CreditsExpiring;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.infrastructure.CreditAccountRepository;
import pe.ayni.wallet.infrastructure.CreditLotRepository;

/** Publishes one advance notice for each unspent credit group that is nearing expiry. */
@Service
public class NotifyExpiringCredits {

  private final CreditLotRepository lots;
  private final CreditAccountRepository accounts;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final Duration noticePeriod;

  NotifyExpiringCredits(
      CreditLotRepository lots,
      CreditAccountRepository accounts,
      ApplicationEventPublisher events,
      Clock clock,
      @Value("${ayni.wallet.expiry-notice-days:7}") int noticeDays) {
    if (noticeDays < 1) {
      throw new IllegalArgumentException("expiry-notice-days must be at least one");
    }
    this.lots = lots;
    this.accounts = accounts;
    this.events = events;
    this.clock = clock;
    this.noticePeriod = Duration.ofDays(noticeDays);
  }

  /** Universities with an unnotified group inside the notice period. */
  @Transactional(readOnly = true)
  public List<String> universitiesWithCreditsExpiring() {
    Instant now = clock.instant();
    return lots.tenantsWithCreditsExpiring(now, now.plus(noticePeriod));
  }

  /**
   * Publishes notices for this university's groups that expire within the configured notice
   * period. The rows are locked and marked in the same transaction as publication, so repeated
   * or concurrent job runs cannot announce a group twice.
   *
   * @return the number of credit groups announced
   */
  @Transactional
  public int forCurrentUniversity() {
    String tenantId = TenantContext.require();
    Instant now = clock.instant();
    List<CreditLot> expiring = lots.lockExpiring(tenantId, now, now.plus(noticePeriod));
    if (expiring.isEmpty()) {
      return 0;
    }

    Map<UUID, UUID> userByAccount = new HashMap<>();
    accounts
        .findByTenantIdAndIdIn(
            tenantId, expiring.stream().map(CreditLot::accountId).distinct().toList())
        .forEach(account -> userByAccount.put(account.id(), account.userId()));

    for (CreditLot lot : expiring) {
      UUID userId = userByAccount.get(lot.accountId());
      if (userId == null) {
        throw new IllegalStateException(
            "Credit account not found for expiring group " + lot.id());
      }
      Credits amount = lot.remaining();
      lot.markExpiryNoticeSent(now);
      events.publishEvent(
          new CreditsExpiring(tenantId, userId, amount, lot.expiresAt(), now));
    }
    return expiring.size();
  }
}
