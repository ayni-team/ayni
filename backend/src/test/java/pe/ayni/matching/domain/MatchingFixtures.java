package pe.ayni.matching.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.domain.model.Standing;

/** Offers for the unit tests, each of a tutor and a block of its own. */
public final class MatchingFixtures {

  public static final String UPC = "UPC";
  public static final UUID DATABASES = UUID.fromString("b0000000-0000-4000-8000-000000000102");

  private MatchingFixtures() {}

  /** A tutor with an average in the course. */
  public static AvailableOffer rated(String name, Instant startsAt, String averageStars) {
    return offer(name, startsAt, new Standing(new BigDecimal(averageStars), 5, 6));
  }

  /** A tutor with too few ratings in the course to show an average. */
  public static AvailableOffer newTutor(String name, Instant startsAt) {
    return offer(name, startsAt, new Standing(null, 2, 2));
  }

  public static AvailableOffer offer(String name, Instant startsAt, Standing standing) {
    return new AvailableOffer(
        UPC, UUID.randomUUID(), DATABASES, UUID.randomUUID(), startsAt, name, standing);
  }
}
