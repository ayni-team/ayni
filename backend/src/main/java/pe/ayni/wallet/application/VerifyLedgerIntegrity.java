package pe.ayni.wallet.application;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.domain.model.LedgerEntry;
import pe.ayni.wallet.domain.model.WalletAccessDeniedException;
import pe.ayni.wallet.domain.services.LedgerChain;
import pe.ayni.wallet.infrastructure.LedgerEntryRepository;

/**
 * Walks a university's whole ledger and says whether it is still the one the application wrote.
 *
 * <p>The chain is read a page at a time, with a cursor on the sequence number, and only the last
 * entry of each page is kept to verify the next one against. After each page the persistence
 * context is emptied, so a ledger of millions of entries costs the memory of one page.
 *
 * <p>It reports where the chain breaks, not who is affected: no amount and no person is in the
 * answer. Repairing a broken chain is not done here; this only finds it.
 */
@Service
public class VerifyLedgerIntegrity {

  private final LedgerEntryRepository entries;
  private final EntityManager entityManager;
  private final IdentityApi identity;
  private final Clock clock;
  private final int pageSize;

  VerifyLedgerIntegrity(
      LedgerEntryRepository entries,
      EntityManager entityManager,
      IdentityApi identity,
      Clock clock,
      @Value("${ayni.wallet.integrity-page-size:500}") int pageSize) {
    this.entries = entries;
    this.entityManager = entityManager;
    this.identity = identity;
    this.clock = clock;
    this.pageSize = pageSize;
  }

  /**
   * Verifies the ledger of the university of the request, for a coordinator who is active.
   *
   * @throws WalletAccessDeniedException for a student and for a coordinator who is restricted
   */
  @Transactional(readOnly = true)
  public LedgerIntegrityReport asCoordinator() {
    UserView user = identity.requireUser(CurrentUser.require());
    if (user.role() != UserRole.COORDINATOR || !identity.isActive(user.id())) {
      throw new WalletAccessDeniedException(
          "Only an active coordinator of the university can verify its ledger");
    }
    return forCurrentUniversity();
  }

  /**
   * Verifies the ledger of the university bound to the current thread.
   *
   * <p>What the daily job calls: no person asks, so there is nobody to check.
   */
  @Transactional(readOnly = true)
  public LedgerIntegrityReport forCurrentUniversity() {

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    long checked = 0;
    LedgerEntry previous = null;
    long after = 0;

    while (true) {
      List<LedgerEntry> page =
          entries.findByTenantIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
              tenantId, after, PageRequest.ofSize(pageSize));
      if (page.isEmpty()) {
        break;
      }

      Optional<LedgerEntry> tampered = LedgerChain.firstTamperedEntry(previous, page);
      if (tampered.isPresent()) {
        LedgerEntry entry = tampered.get();
        long position = checked + page.indexOf(entry) + 1;
        return new LedgerIntegrityReport(
            false,
            position,
            now,
            entry.id(),
            position,
            entry.sequenceNumber(),
            entries.countAccountsWhoseBalanceDisagrees(tenantId, now));
      }

      checked += page.size();
      previous = page.getLast();
      after = previous.sequenceNumber();
      // The page is verified and nothing keeps it: let it go before reading the next one.
      entityManager.clear();
    }

    return new LedgerIntegrityReport(
        true,
        checked,
        now,
        null,
        null,
        null,
        entries.countAccountsWhoseBalanceDisagrees(tenantId, now));
  }
}
