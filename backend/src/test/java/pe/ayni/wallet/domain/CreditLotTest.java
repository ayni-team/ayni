package pe.ayni.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.wallet.domain.WalletFixtures.ACCOUNT;
import static pe.ayni.wallet.domain.WalletFixtures.CLOCK;
import static pe.ayni.wallet.domain.WalletFixtures.NOW;
import static pe.ayni.wallet.domain.WalletFixtures.UPC;
import static pe.ayni.wallet.domain.WalletFixtures.earned;
import static pe.ayni.wallet.domain.WalletFixtures.purchased;
import static pe.ayni.wallet.domain.WalletFixtures.seedExpiredDaysAgo;
import static pe.ayni.wallet.domain.WalletFixtures.seedExpiringInDays;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.CreditRuleViolation;
import pe.ayni.wallet.domain.model.CreditSource;

/** What a group of credits does and does not allow. */
class CreditLotTest {

  @ParameterizedTest
  @EnumSource(
      value = CreditType.class,
      names = {"EARNED", "PURCHASED"})
  @DisplayName("credits that are earned or paid for cannot be given an expiry date")
  void creditsThatNeverExpireRefuseAnExpiryDate(CreditType type) {

    assertThat(type.expires()).isFalse();

    assertThatThrownBy(
            () ->
                CreditLot.granted(
                    UPC,
                    ACCOUNT,
                    Credits.of(3),
                    type,
                    NOW.plusSeconds(86_400),
                    CreditSource.of(type),
                    UUID.randomUUID(),
                    NOW))
        .isInstanceOf(CreditRuleViolation.class)
        .hasMessageContaining("never expire");
  }

  @ParameterizedTest
  @EnumSource(
      value = CreditType.class,
      names = {"SEED", "ALLOCATED"})
  @DisplayName("credits granted by the university must carry an expiry date")
  void creditsThatExpireRequireAnExpiryDate(CreditType type) {

    assertThat(type.expires()).isTrue();

    assertThatThrownBy(
            () ->
                CreditLot.granted(
                    UPC,
                    ACCOUNT,
                    Credits.of(3),
                    type,
                    null,
                    CreditSource.of(type),
                    UUID.randomUUID(),
                    NOW))
        .isInstanceOf(CreditRuleViolation.class)
        .hasMessageContaining("must carry an expiry date");
  }

  @Test
  @DisplayName("earned credits are the only ones that count towards recognition")
  void onlyEarnedCreditsCountTowardsRecognition() {
    assertThat(earned(4).countsTowardsRecognition()).isTrue();
    assertThat(purchased(4).countsTowardsRecognition()).isFalse();
    assertThat(seedExpiringInDays(4, 30).countsTowardsRecognition()).isFalse();
  }

  @Test
  @DisplayName("a group that has not reached its date is spendable, one that has is not")
  void knowsWhetherItIsStillAlive() {

    assertThat(seedExpiringInDays(3, 1).isSpendableAt(CLOCK.instant())).isTrue();
    assertThat(seedExpiredDaysAgo(3, 1).isSpendableAt(CLOCK.instant())).isFalse();
    // Credits that never expire are spendable whenever they still hold something.
    assertThat(earned(3).isSpendableAt(CLOCK.instant())).isTrue();
  }

  @Test
  @DisplayName("the moment of expiry is the first one at which the credits are gone")
  void expiresAtTheExactInstant() {

    CreditLot lot = seedExpiringInDays(3, 1);

    assertThat(lot.isExpiredAt(lot.expiresAt().minusSeconds(1))).isFalse();
    assertThat(lot.isExpiredAt(lot.expiresAt())).isTrue();
  }

  @Test
  @DisplayName("spending takes credits out and refunding puts them back")
  void consumesAndRestores() {

    CreditLot lot = seedExpiringInDays(5, 30);

    lot.consume(Credits.of(3));
    assertThat(lot.remaining()).isEqualTo(Credits.of(2));

    lot.restore(Credits.of(3));
    assertThat(lot.remaining()).isEqualTo(Credits.of(5));
  }

  @Test
  @DisplayName("a group cannot give more than it holds")
  void refusesToSpendMoreThanItHolds() {
    CreditLot lot = seedExpiringInDays(2, 30);
    assertThatThrownBy(() -> lot.consume(Credits.of(3)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a refund cannot put back more than the group ever gave")
  void refusesToRestoreMoreThanItGave() {

    CreditLot lot = seedExpiringInDays(5, 30);
    lot.consume(Credits.of(1));

    // Otherwise cancelling a booking would be a way of creating credits.
    assertThatThrownBy(() -> lot.restore(Credits.of(2)))
        .isInstanceOf(CreditRuleViolation.class)
        .hasMessageContaining("Cannot return");
  }

  @Test
  @DisplayName("expiring empties the group and says what was lost")
  void expiringEmptiesTheGroup() {

    CreditLot lot = seedExpiredDaysAgo(4, 2);

    assertThat(lot.expire()).isEqualTo(Credits.of(4));
    assertThat(lot.remaining()).isEqualTo(Credits.ZERO);
    // The row stays, with what it originally held, so the history can still show it.
    assertThat(lot.original()).isEqualTo(Credits.of(4));
  }
}
