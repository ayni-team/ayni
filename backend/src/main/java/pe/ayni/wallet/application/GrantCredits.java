package pe.ayni.wallet.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.CreditsGranted;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.domain.model.CreditAccount;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.CreditSource;
import pe.ayni.wallet.domain.model.Movement;
import pe.ayni.wallet.infrastructure.CreditLotRepository;

/**
 * Places credits in a student's account.
 *
 * <p>Every credit in the product arrives through here: the university's initial grant, a session
 * taught, a purchase. Each arrival is a new group, never an addition to an existing one, because
 * the group is what carries the origin and the expiry those credits were given with.
 */
@Service
class GrantCredits {

  private final Accounts accounts;
  private final CreditLotRepository lots;
  private final LedgerWriter ledger;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  GrantCredits(
      Accounts accounts,
      CreditLotRepository lots,
      LedgerWriter ledger,
      ApplicationEventPublisher events,
      Clock clock) {
    this.accounts = accounts;
    this.lots = lots;
    this.ledger = ledger;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Grants credits of one origin, opening the account if the student did not have one.
   *
   * @param expiresAt required for SEED and ALLOCATED, {@code null} for EARNED and PURCHASED
   * @param sourceId what caused the grant: the policy, the session, the purchase
   */
  @Transactional
  void grant(UUID userId, Credits amount, CreditType type, Instant expiresAt, UUID sourceId) {

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    CreditAccount account = accounts.openIfAbsent(userId);
    CreditSource source = CreditSource.of(type);

    CreditLot lot =
        CreditLot.granted(tenantId, account.id(), amount, type, expiresAt, source, sourceId, now);
    lots.save(lot);

    ledger.append(Movement.credit(lot, amount, source.reason(), source.referenceType(), sourceId));

    // Said out loud so that notifications can tell the student, without wallet knowing that
    // notifications exists.
    events.publishEvent(new CreditsGranted(tenantId, userId, amount, type, expiresAt, now));
  }
}
