package pe.ayni.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.wallet.domain.WalletFixtures.CLOCK;
import static pe.ayni.wallet.domain.WalletFixtures.earned;
import static pe.ayni.wallet.domain.WalletFixtures.purchased;
import static pe.ayni.wallet.domain.WalletFixtures.seedExpiredDaysAgo;
import static pe.ayni.wallet.domain.WalletFixtures.seedExpiringInDays;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.InsufficientCreditsException;
import pe.ayni.wallet.domain.model.Allocation;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.services.SpendingPlan;

/** The order credits are spent in, which is the rule booking depends on. */
class SpendingPlanTest {

  @Test
  @DisplayName("spends the group that expires first")
  void spendsWhatExpiresFirst() {

    CreditLot december = seedExpiringInDays(5, 90);
    CreditLot october = seedExpiringInDays(5, 10);

    SpendingPlan plan =
        SpendingPlan.of(List.of(december, october), Credits.of(3), CLOCK.instant());

    assertThat(plan.allocations()).hasSize(1);
    assertThat(plan.allocations().getFirst().lot()).isSameAs(october);
    assertThat(plan.allocations().getFirst().amount()).isEqualTo(Credits.of(3));
  }

  @Test
  @DisplayName("moves on to the next group when the first one runs out")
  void takesFromSeveralGroupsInOrder() {

    CreditLot october = seedExpiringInDays(2, 10);
    CreditLot december = seedExpiringInDays(5, 90);

    SpendingPlan plan =
        SpendingPlan.of(List.of(december, october), Credits.of(4), CLOCK.instant());

    assertThat(plan.allocations()).extracting(Allocation::lot).containsExactly(october, december);
    assertThat(plan.allocations())
        .extracting(Allocation::amount)
        .containsExactly(Credits.of(2), Credits.of(2));
    assertThat(plan.total()).isEqualTo(Credits.of(4));
  }

  @Test
  @DisplayName("keeps for last what never expires, however old it is")
  void spendsCreditsThatExpireBeforeCreditsThatDoNot() {

    CreditLot earnedLongAgo = earned(10);
    CreditLot purchasedLongAgo = purchased(10);
    CreditLot grant = seedExpiringInDays(4, 200);

    SpendingPlan plan =
        SpendingPlan.of(
            List.of(earnedLongAgo, purchasedLongAgo, grant), Credits.of(4), CLOCK.instant());

    // What was earned and what was paid for are the student's to keep: they are spent only once
    // there is nothing left that would otherwise be lost.
    assertThat(plan.allocations()).extracting(Allocation::lot).containsExactly(grant);
  }

  @Test
  @DisplayName("ignores groups that already expired, however many credits they hold")
  void ignoresExpiredGroups() {

    CreditLot dead = seedExpiredDaysAgo(50, 5);
    CreditLot alive = seedExpiringInDays(3, 30);

    SpendingPlan plan = SpendingPlan.of(List.of(dead, alive), Credits.of(3), CLOCK.instant());

    assertThat(plan.allocations()).extracting(Allocation::lot).containsExactly(alive);
  }

  @Test
  @DisplayName("refuses the charge and says how many credits are missing")
  void refusesWhenThereIsNotEnough() {

    CreditLot alive = seedExpiringInDays(2, 30);
    CreditLot dead = seedExpiredDaysAgo(10, 1);

    assertThatThrownBy(() -> SpendingPlan.of(List.of(alive, dead), Credits.of(5), CLOCK.instant()))
        .isInstanceOf(InsufficientCreditsException.class)
        .extracting(failure -> ((InsufficientCreditsException) failure).missing())
        // Three, not thirteen: the ten credits that expired are gone.
        .isEqualTo(Credits.of(3));
  }

  @Test
  @DisplayName("refuses a charge against an empty wallet")
  void refusesWhenThereIsNothing() {

    assertThatThrownBy(() -> SpendingPlan.of(List.of(), Credits.of(2), CLOCK.instant()))
        .isInstanceOf(InsufficientCreditsException.class)
        .extracting(failure -> ((InsufficientCreditsException) failure).missing())
        .isEqualTo(Credits.of(2));
  }
}
