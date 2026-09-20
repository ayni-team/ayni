package pe.ayni.wallet.domain.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;

/**
 * What a student can spend, worked out from their groups of credits.
 *
 * <p>This is derived, never stored. Asking the groups every time costs one indexed query and
 * removes a whole class of bug: a balance column that disagrees with the history is possible, and
 * once it happens there is no way to tell which of the two is right.
 *
 * @param available everything still spendable at the moment it was computed
 * @param byType the same credits split by where they came from, which is what the student is shown
 */
public record Balance(Credits available, List<TypeBreakdown> byType) {

  public Balance {
    byType = List.copyOf(byType);
  }

  /**
   * The credits of one origin, and the groups they sit in.
   *
   * @param expires whether credits of this type carry an expiry at all
   * @param countsTowardsRecognition whether they count towards the recognition the university may
   *     grant, which is true of earned credits and of nothing else
   */
  public record TypeBreakdown(
      CreditType type,
      Credits available,
      boolean expires,
      boolean countsTowardsRecognition,
      List<Group> groups) {

    public TypeBreakdown {
      groups = List.copyOf(groups);
    }
  }

  /**
   * A group of credits as the student sees it: how many are left and when they die.
   *
   * @param expiresAt {@code null} for credits that never expire
   */
  public record Group(Credits amount, Instant expiresAt) {}

  /**
   * Derives the balance from the groups of an account.
   *
   * <p>Expired groups are left out: their credits are gone, even though their rows stay where they
   * are and their expiry is still in the history.
   */
  public static Balance from(Collection<CreditLot> lots, Instant now) {

    List<CreditLot> live = lots.stream().filter(lot -> lot.isSpendableAt(now)).toList();

    Map<CreditType, List<CreditLot>> byType =
        live.stream().collect(Collectors.groupingBy(CreditLot::creditType));

    List<TypeBreakdown> breakdowns =
        byType.entrySet().stream()
            .map(entry -> breakdownOf(entry.getKey(), entry.getValue()))
            // A fixed order, so the same wallet always reads the same way.
            .sorted(Comparator.comparing(TypeBreakdown::type))
            .toList();

    Credits available =
        breakdowns.stream().map(TypeBreakdown::available).reduce(Credits.ZERO, Credits::plus);

    return new Balance(available, breakdowns);
  }

  private static TypeBreakdown breakdownOf(CreditType type, List<CreditLot> lots) {

    List<Group> groups =
        lots.stream()
            .sorted(CreditLot.EXPIRY_FIRST)
            .map(lot -> new Group(lot.remaining(), lot.expiresAt()))
            .toList();

    Credits available = lots.stream().map(CreditLot::remaining).reduce(Credits.ZERO, Credits::plus);

    return new TypeBreakdown(
        type, available, type.expires(), type.countsTowardsRecognition(), groups);
  }

  /** The balance of a student who has never been granted anything. */
  public static Balance empty() {
    return new Balance(Credits.ZERO, List.of());
  }

  /** Whether there is nothing left to spend. */
  public boolean isEmpty() {
    return available.isZero();
  }
}
