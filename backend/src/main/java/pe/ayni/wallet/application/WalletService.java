package pe.ayni.wallet.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.ChargeReceipt;
import pe.ayni.wallet.CreditBalanceView;
import pe.ayni.wallet.WalletApi;

/**
 * What the other modules see of wallet.
 *
 * <p>It holds no rules of its own: each operation is one use case, and this class is the door they
 * are reached through. Booking depends on {@link WalletApi} and never learns that {@code
 * ChargeCredits} exists, so the inside of wallet can be rearranged without anybody else changing.
 */
@Service
public class WalletService implements WalletApi {

  private final WalletBalanceQuery balance;
  private final ChargeCredits chargeCredits;
  private final RefundBooking refundBooking;
  private final GrantCredits grantCredits;

  WalletService(
      WalletBalanceQuery balance,
      ChargeCredits chargeCredits,
      RefundBooking refundBooking,
      GrantCredits grantCredits) {
    this.balance = balance;
    this.chargeCredits = chargeCredits;
    this.refundBooking = refundBooking;
    this.grantCredits = grantCredits;
  }

  @Override
  public CreditBalanceView balanceOf(UUID userId) {
    return new CreditBalanceView(userId, balance.of(userId).available());
  }

  @Override
  public ChargeReceipt charge(UUID userId, Credits amount, UUID bookingId) {
    return chargeCredits.charge(userId, amount, bookingId);
  }

  @Override
  public void refund(UUID bookingId) {
    refundBooking.refund(bookingId);
  }

  @Override
  public void grant(
      UUID userId, Credits amount, CreditType type, Instant expiresAt, UUID sourceId) {
    grantCredits.grant(userId, amount, type, expiresAt, sourceId);
  }

  @Override
  public Credits earnedTotal(UUID userId) {
    return balance.earnedTotal(userId);
  }
}
