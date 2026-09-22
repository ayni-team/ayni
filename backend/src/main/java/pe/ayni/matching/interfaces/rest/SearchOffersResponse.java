package pe.ayni.matching.interfaces.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.matching.application.AvailableOfferView;
import pe.ayni.matching.application.SearchAvailableOffersUseCase;

/** One bookable hour, as returned to the client. */
record OfferResponse(
        UUID offerId,
        UUID tutorId,
        UUID courseId,
        Instant startsAt,
        Instant endsAt,
        BigDecimal tutorRating,
        boolean newTutor) {

    static OfferResponse of(AvailableOfferView view) {
        return new OfferResponse(
                view.offerId(),
                view.tutorId(),
                view.courseId(),
                view.startsAt(),
                view.endsAt(),
                view.tutorRating(),
                view.newTutor());
    }
}

/**
 * The search result. {@code exactMatch} is {@code false} when nothing was found in the requested
 * range and {@code offers} holds the closest blocks outside it instead (US01, scenario 3).
 */
record SearchOffersResponse(List<OfferResponse> offers, boolean exactMatch) {

    static SearchOffersResponse of(SearchAvailableOffersUseCase.Result result) {
        return new SearchOffersResponse(
                result.offers().stream().map(OfferResponse::of).toList(), result.exactMatch());
    }
}