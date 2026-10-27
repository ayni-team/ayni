package pe.ayni.matching.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.matching.domain.MatchingFixtures.newTutor;
import static pe.ayni.matching.domain.MatchingFixtures.rated;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.domain.model.SearchWindow;
import pe.ayni.matching.domain.services.NearestOffers;

/** US01, scenario 2: no tutor free in the window, so the closest hours outside it come back. */
class NearestOffersTest {

  private static final Instant TWO_PM = Instant.parse("2026-09-30T19:00:00Z");
  private static final Instant SIX_PM = Instant.parse("2026-09-30T23:00:00Z");
  private static final SearchWindow AFTERNOON = new SearchWindow(TWO_PM, SIX_PM);

  private static Instant hoursAfterSix(int hours) {
    return SIX_PM.plus(Duration.ofHours(hours));
  }

  private static Instant hoursBeforeTwo(int hours) {
    return TWO_PM.minus(Duration.ofHours(hours));
  }

  @Test
  @DisplayName("the closest hours on either side come back, in the order a search reads")
  void theClosestOnEitherSide() {

    AvailableOffer threeHoursBefore = rated("Ana", hoursBeforeTwo(3), "4.00");
    AvailableOffer oneHourBefore = rated("Bruno", hoursBeforeTwo(1), "4.00");
    AvailableOffer atSix = rated("Carla", hoursAfterSix(0), "4.00");
    AvailableOffer twoHoursAfter = rated("Diego", hoursAfterSix(2), "4.00");

    List<AvailableOffer> nearest =
        NearestOffers.around(
            AFTERNOON,
            List.of(oneHourBefore, threeHoursBefore),
            List.of(atSix, twoHoursAfter),
            3);

    assertThat(nearest).containsExactly(oneHourBefore, atSix, twoHoursAfter);
  }

  @Test
  @DisplayName("several tutors at the closest hour keep the best average first and new tutors last")
  void tutorsAtTheSameHourKeepTheSearchOrder() {

    AvailableOffer newcomer = newTutor("Aaron", hoursAfterSix(1));
    AvailableOffer good = rated("Zoe", hoursAfterSix(1), "4.20");
    AvailableOffer best = rated("Mia", hoursAfterSix(1), "4.90");

    assertThat(NearestOffers.around(AFTERNOON, List.of(), List.of(newcomer, good, best), 10))
        .containsExactly(best, good, newcomer);
  }

  @Test
  @DisplayName("nothing on either side is an empty answer")
  void nothingAnywhere() {

    assertThat(NearestOffers.around(AFTERNOON, List.of(), List.of(), 20)).isEmpty();
  }

  @Test
  @DisplayName("a limit below one is a mistake")
  void aLimitBelowOneIsAMistake() {

    assertThatThrownBy(() -> NearestOffers.around(AFTERNOON, List.of(), List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
