package pe.ayni.matching.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row the search hands back: everything a student needs to compare offers and pick one.
 *
 * <p>{@code startsAt}/{@code endsAt} are UTC, as stored; converting to the student's university
 * time zone is the presentation layer's job, not this use case's.
 */
public record AvailableOfferView(
        UUID offerId,
        UUID tutorId,
        UUID courseId,
        UUID sourceHourBlockId,
        Instant startsAt,
        Instant endsAt,
        BigDecimal tutorRating,
        boolean newTutor) {

    static AvailableOfferView from(pe.ayni.matching.domain.model.AvailableOffer offer) {
        return new AvailableOfferView(
                offer.getId(),
                offer.getTutorId(),
                offer.getCourseId(),
                offer.getSourceHourBlockId(),
                offer.getStartsAt(),
                offer.getEndsAt(),
                offer.getTutorRating(),
                offer.isNewTutor());
    }
}