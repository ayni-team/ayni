package pe.ayni.wallet.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.wallet.domain.model.CreditLot;

/**
 * Groups of credits.
 *
 * <p>Two of these queries take a write lock. Credits are the one thing in this product that can be
 * spent twice if two requests read the same groups at the same time, and the groups carry no
 * version column, so the rows are locked while they are being spent.
 */
public interface CreditLotRepository extends JpaRepository<CreditLot, UUID> {

  /** Every group of an account, spent or not, expired or not: the history needs all of them. */
  List<CreditLot> findByTenantIdAndAccountId(String tenantId, UUID accountId);

  /** The given groups, locked, for a refund that is about to put credits back in them. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select lot from CreditLot lot
      where lot.tenantId = :tenantId and lot.id in :ids
      """)
  List<CreditLot> lockByIds(
      @Param("tenantId") String tenantId, @Param("ids") Collection<UUID> ids);

  /**
   * The groups a charge may take from, locked until the transaction ends.
   *
   * <p>They come back unordered on purpose: the order credits are spent in is a rule of the
   * product, and it belongs in {@code SpendingPlan} where it can be read and tested, not in an
   * {@code ORDER BY} that nobody reviews.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select lot from CreditLot lot
      where lot.tenantId = :tenantId
        and lot.accountId = :accountId
        and lot.remainingAmount > 0
        and (lot.expiresAt is null or lot.expiresAt > :now)
      """)
  List<CreditLot> lockSpendable(
      @Param("tenantId") String tenantId,
      @Param("accountId") UUID accountId,
      @Param("now") Instant now);

  /** The groups that have reached their expiry with credits still in them. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select lot from CreditLot lot
      where lot.tenantId = :tenantId
        and lot.remainingAmount > 0
        and lot.expiresAt is not null
        and lot.expiresAt <= :now
      order by lot.accountId, lot.expiresAt
      """)
  List<CreditLot> lockExpired(@Param("tenantId") String tenantId, @Param("now") Instant now);

  /**
   * The universities that have credits to expire.
   *
   * <p>The scheduled job runs outside any request, so there is no tenant bound to ask for. When
   * identity exists this becomes {@code IdentityApi.activeTenantCodes()}; until then wallet can
   * answer it from its own data without reaching into another module.
   */
  @Query(
      """
      select distinct lot.tenantId from CreditLot lot
      where lot.remainingAmount > 0
        and lot.expiresAt is not null
        and lot.expiresAt <= :now
      """)
  List<String> tenantsWithCreditsToExpire(@Param("now") Instant now);

  /**
   * Everything the student was ever granted of one type, spent or not.
   *
   * <p>Recognition asks for the earned total, and what backs a recognition request is the teaching
   * that happened, not what is left in the wallet afterwards.
   */
  @Query(
      """
      select coalesce(sum(lot.originalAmount), 0) from CreditLot lot
      where lot.tenantId = :tenantId
        and lot.accountId = :accountId
        and lot.creditType = :creditType
      """)
  long sumOriginalAmount(
      @Param("tenantId") String tenantId,
      @Param("accountId") UUID accountId,
      @Param("creditType") CreditType creditType);
}
