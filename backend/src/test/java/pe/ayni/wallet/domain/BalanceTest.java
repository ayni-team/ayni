package pe.ayni.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.ayni.wallet.domain.WalletFixtures.CLOCK;
import static pe.ayni.wallet.domain.WalletFixtures.earned;
import static pe.ayni.wallet.domain.WalletFixtures.purchased;
import static pe.ayni.wallet.domain.WalletFixtures.seedExpiredDaysAgo;
import static pe.ayni.wallet.domain.WalletFixtures.seedExpiringInDays;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.domain.model.Balance;

/** The balance US23 shows: the total, where it came from, and what dies when. */
class BalanceTest {

  @Test
  @DisplayName("adds up what is left in the groups that are still alive")
  void addsUpTheLiveGroups() {

    Balance balance =
        Balance.from(
            List.of(seedExpiringInDays(6, 30), earned(4), purchased(2)), CLOCK.instant());

    assertThat(balance.available()).isEqualTo(Credits.of(12));
    assertThat(balance.byType())
        .extracting(Balance.TypeBreakdown::type)
        .containsExactly(CreditType.SEED, CreditType.EARNED, CreditType.PURCHASED);
  }

  @Test
  @DisplayName("leaves expired credits out of the balance")
  void leavesExpiredCreditsOut() {

    Balance balance =
        Balance.from(List.of(seedExpiringInDays(6, 30), seedExpiredDaysAgo(9, 1)), CLOCK.instant());

    assertThat(balance.available()).isEqualTo(Credits.of(6));
    assertThat(balance.byType()).hasSize(1);
    assertThat(balance.byType().getFirst().groups()).hasSize(1);
  }

  @Test
  @DisplayName("shows the expiry of the groups that have one, and none for the others")
  void showsTheExpiryOfEachGroup() {

    Balance balance =
        Balance.from(List.of(seedExpiringInDays(6, 30), earned(4)), CLOCK.instant());

    Balance.TypeBreakdown seed = breakdownOf(balance, CreditType.SEED);
    assertThat(seed.expires()).isTrue();
    assertThat(seed.groups().getFirst().expiresAt()).isNotNull();

    Balance.TypeBreakdown teaching = breakdownOf(balance, CreditType.EARNED);
    assertThat(teaching.expires()).isFalse();
    assertThat(teaching.groups().getFirst().expiresAt()).isNull();
  }

  @Test
  @DisplayName("marks earned credits as the only ones that count towards recognition")
  void marksWhatCountsTowardsRecognition() {

    Balance balance =
        Balance.from(
            List.of(seedExpiringInDays(6, 30), earned(4), purchased(2)), CLOCK.instant());

    assertThat(breakdownOf(balance, CreditType.EARNED).countsTowardsRecognition()).isTrue();
    assertThat(breakdownOf(balance, CreditType.SEED).countsTowardsRecognition()).isFalse();
    assertThat(breakdownOf(balance, CreditType.PURCHASED).countsTowardsRecognition()).isFalse();
  }

  @Test
  @DisplayName("lists the groups of a type closest to expiring first")
  void listsGroupsInTheOrderTheyWillBeSpent() {

    Balance balance =
        Balance.from(
            List.of(seedExpiringInDays(6, 90), seedExpiringInDays(2, 10)), CLOCK.instant());

    assertThat(breakdownOf(balance, CreditType.SEED).groups())
        .extracting(Balance.Group::amount)
        .containsExactly(Credits.of(2), Credits.of(6));
  }

  @Test
  @DisplayName("a wallet whose credits all expired is empty")
  void isEmptyWhenEverythingExpired() {

    Balance balance = Balance.from(List.of(seedExpiredDaysAgo(5, 3)), CLOCK.instant());

    assertThat(balance.isEmpty()).isTrue();
    assertThat(balance.byType()).isEmpty();
    assertThat(Balance.empty().isEmpty()).isTrue();
  }

  private static Balance.TypeBreakdown breakdownOf(Balance balance, CreditType type) {
    return balance.byType().stream()
        .filter(breakdown -> breakdown.type() == type)
        .findFirst()
        .orElseThrow();
  }
}
