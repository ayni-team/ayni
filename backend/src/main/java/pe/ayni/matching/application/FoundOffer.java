package pe.ayni.matching.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.matching.domain.model.AvailableOffer;

/**
 * One hour a student found, with what they need to choose between tutors.
 *
 * @param averageStars {@code null} while the tutor is new in the course
 * @param newTutor whether the tutor has too few ratings in the course to show an average
 */
public record FoundOffer(
    UUID blockId,
    UUID tutorId,
    String tutorName,
    UUID catalogItemId,
    Instant startsAt,
    Instant endsAt,
    BigDecimal averageStars,
    int ratingsCount,
    int sessionsTaught,
    boolean newTutor) {

  static FoundOffer of(AvailableOffer offer) {
    return new FoundOffer(
        offer.getBlockId(),
        offer.getTutorId(),
        offer.getTutorName(),
        offer.getCatalogItemId(),
        offer.getStartsAt(),
        offer.getEndsAt(),
        offer.getAverageStars(),
        offer.getRatingsCount(),
        offer.getSessionsTaught(),
        offer.isNewTutor());
  }
}
