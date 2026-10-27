package pe.ayni.wallet.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.LedgerEntry;
import pe.ayni.wallet.domain.model.LedgerReason;
import pe.ayni.wallet.infrastructure.CreditLotRepository;
import pe.ayni.wallet.infrastructure.LedgerEntryRepository;

/**
 * Answers what happened to a student's credits.
 *
 * <p>The history is the ledger, read back. Expired credits are in it and stay in it: they no longer
 * count towards the balance, but the student can still see the grant that arrived and the day it
 * died.
 */
@Service
public class MovementHistoryQuery {

  /**
   * Stands in for "no end date". Comparing against a bound is one query; comparing against a
   * parameter that may be null is one query per combination of filters.
   */
  private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

  private final Accounts accounts;
  private final LedgerEntryRepository entries;
  private final CreditLotRepository lots;

  MovementHistoryQuery(
      Accounts accounts, LedgerEntryRepository entries, CreditLotRepository lots) {
    this.accounts = accounts;
    this.entries = entries;
    this.lots = lots;
  }

  /**
   * A page of the history, newest first.
   *
   * @param from first moment included, or {@code null} for the beginning
   * @param to first moment excluded, or {@code null} for no end
   * @param reason the kind of movement to keep, or {@code null} for all of them
   */
  @Transactional(readOnly = true)
  public Page<MovementView> of(
      UUID userId, Instant from, Instant to, LedgerReason reason, int page, int size) {

    String tenantId = TenantContext.require();

    return accounts
        .find(userId)
        .map(
            account -> {
              Page<LedgerEntry> found =
                  entries.findHistory(
                      tenantId,
                      account.id(),
                      from == null ? Instant.EPOCH : from,
                      to == null ? FAR_FUTURE : to,
                      reason == null ? List.of(LedgerReason.values()) : List.of(reason),
                      pageRequest(page, size));
              return describe(tenantId, found);
            })
        // A student with no account has no history, which is an empty page and not a refusal.
        .orElseGet(() -> new PageImpl<>(List.of(), pageRequest(page, size), 0));
  }

  private static PageRequest pageRequest(int page, int size) {
    // Sequence breaks ties: two movements of the same charge share an instant, and a page boundary
    // falling between them must not show one of them twice.
    return PageRequest.of(
        page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "sequenceNumber"));
  }

  /** Adds to every entry the type and expiry of the group it moved credits in or out of. */
  private Page<MovementView> describe(String tenantId, Page<LedgerEntry> found) {

    Set<UUID> lotIds =
        found.getContent().stream()
            .map(LedgerEntry::lotId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, CreditLot> lotsById =
        lotIds.isEmpty()
            ? Map.of()
            : lots.findByTenantIdAndIdIn(tenantId, lotIds).stream()
                .collect(Collectors.toMap(CreditLot::id, Function.identity()));

    return found.map(
        entry -> {
          CreditLot lot = entry.lotId() == null ? null : lotsById.get(entry.lotId());
          return new MovementView(
              entry.sequenceNumber(),
              entry.occurredAt(),
              entry.direction(),
              entry.amount(),
              entry.reason(),
              lot == null ? null : lot.creditType(),
              lot == null ? null : lot.expiresAt(),
              entry.referenceType(),
              entry.referenceId());
        });
  }
}
