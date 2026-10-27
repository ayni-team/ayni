package pe.ayni.wallet;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;

/**
 * What wallet offers to the other modules. Wallet is the only module that writes the ledger.
 *
 * <p>Implemented by a class in {@code wallet.application}.
 */
public interface WalletApi {

  CreditBalanceView balanceOf(UUID userId);

  /**
   * Charges a booking, spending first the credits closest to expiring.
   *
   * <p>Runs inside the caller's transaction: if the booking fails afterwards, the charge rolls back
   * with it.
   *
   * @throws InsufficientCreditsException when the balance does not cover the amount
   */
  ChargeReceipt charge(UUID userId, Credits amount, UUID bookingId);

  /** Returns the credits of a booking to the groups they came from, with the expiry they had. */
  void refund(UUID bookingId);

  /**
   * Places credits in an account.
   *
   * @param expiresAt required for SEED and ALLOCATED, {@code null} for EARNED and PURCHASED
   */
  void grant(UUID userId, Credits amount, CreditType type, Instant expiresAt, UUID sourceId);

  /** Every credit the user ever earned by teaching, spent or not. */
  Credits earnedTotal(UUID userId);
}
