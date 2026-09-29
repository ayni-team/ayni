package pe.ayni.matching.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.ayni.matching.domain.MatchingFixtures.newTutor;
import static pe.ayni.matching.domain.MatchingFixtures.offer;
import static pe.ayni.matching.domain.MatchingFixtures.rated;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.domain.model.Standing;

class AvailableOfferTest {

  private static final Instant FIVE_PM = Instant.parse("2026-09-30T22:00:00Z");
  private static final Instant SIX_PM = Instant.parse("2026-09-30T23:00:00Z");

  @Test
  @DisplayName("an offer lasts one hour, the length of every block")
  void anOfferLastsOneHour() {

    AvailableOffer offer = rated("Bruno Salas", FIVE_PM, "4.50");

    assertThat(offer.getEndsAt()).isEqualTo(SIX_PM);
  }

  @Test
  @DisplayName("a tutor without an average is new, whatever was counted")
  void aTutorWithoutAnAverageIsNew() {

    assertThat(newTutor("Carla Ruiz", FIVE_PM).isNewTutor()).isTrue();
    assertThat(offer("Diego Paz", FIVE_PM, Standing.none()).isNewTutor()).isTrue();
    assertThat(rated("Bruno Salas", FIVE_PM, "3.00").isNewTutor()).isFalse();
  }

  @Test
  @DisplayName("a tutor who never taught the course has no average and nothing counted")
  void noStandingMeansNothingCounted() {

    AvailableOffer offer = offer("Diego Paz", FIVE_PM, Standing.none());

    assertThat(offer.getAverageStars()).isNull();
    assertThat(offer.getRatingsCount()).isZero();
    assertThat(offer.getSessionsTaught()).isZero();
  }

  @Test
  @DisplayName("search order: by time, then the best average, with new tutors last")
  void searchOrder() {

    AvailableOffer laterButBest = rated("Ana", SIX_PM, "5.00");
    AvailableOffer newAtFive = newTutor("Aaron", FIVE_PM);
    AvailableOffer goodAtFive = rated("Zoe", FIVE_PM, "4.20");
    AvailableOffer bestAtFive = rated("Mia", FIVE_PM, "4.80");

    List<AvailableOffer> sorted =
        Stream.of(laterButBest, newAtFive, goodAtFive, bestAtFive)
            .sorted(AvailableOffer.SEARCH_ORDER)
            .toList();

    assertThat(sorted).containsExactly(bestAtFive, goodAtFive, newAtFive, laterButBest);
  }

  @Test
  @DisplayName("an offer built here has not been stored yet, so it is inserted without a read")
  void aNewOfferIsNew() {

    assertThat(rated("Bruno Salas", FIVE_PM, "4.50").isNew()).isTrue();
  }
}
