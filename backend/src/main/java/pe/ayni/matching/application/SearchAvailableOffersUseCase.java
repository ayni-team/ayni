package pe.ayni.matching.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.ayni.matching.domain.model.AvailableOffer;
import pe.ayni.matching.infrastructure.AvailableOfferRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Finds bookable hours for a course within a date range.
 *
 * <p>When nothing falls inside the range, the closest blocks outside it are returned instead of an
 * empty list (US01, scenario 3): a search that finds nothing to compare against is worse than one
 * that finds something slightly off.
 */
@Service
public class SearchAvailableOffersUseCase {

    /** How many blocks the fallback looks at on each side of the range before merging and cutting. */
    private static final int FALLBACK_CANDIDATES_PER_SIDE = 10;

    /** How many results the fallback returns once the two sides are merged. */
    private static final int FALLBACK_RESULT_LIMIT = 10;

    private final AvailableOfferRepository offers;

    SearchAvailableOffersUseCase(AvailableOfferRepository offers) {
        this.offers = offers;
    }

    /** Searches within [from, to), both in UTC. */
    public List<AvailableOfferView> execute(UUID courseId, Instant from, Instant to) {
        String tenantId = TenantContext.require();

        List<AvailableOffer> withinRange =
                offers.findByTenantIdAndCourseIdAndStartsAtBetweenOrderByStartsAtAscTutorRatingDesc(
                        tenantId, courseId, from, to);

        if (!withinRange.isEmpty()) {
            return withinRange.stream().map(AvailableOfferView::from).toList();
        }

        return nearestOutsideRange(tenantId, courseId, from, to);
    }

    private List<AvailableOfferView> nearestOutsideRange(
            String tenantId, UUID courseId, Instant from, Instant to) {

        List<AvailableOffer> after =
                offers.findTop10ByTenantIdAndCourseIdAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
                        tenantId, courseId, to);
        List<AvailableOffer> before =
                offers.findTop10ByTenantIdAndCourseIdAndStartsAtLessThanOrderByStartsAtDesc(
                        tenantId, courseId, from);

        // Distance to the edge of the range it fell outside of: how close it is to what the student
        // actually asked for, not to some arbitrary point in time.
        Comparator<AvailableOffer> byDistanceToRange =
                Comparator.comparing(
                        offer ->
                                offer.getStartsAt().isBefore(from)
                                        ? Duration.between(offer.getStartsAt(), from)
                                        : Duration.between(to, offer.getStartsAt()));

        return java.util.stream.Stream.concat(after.stream(), before.stream())
                .sorted(byDistanceToRange)
                .limit(FALLBACK_RESULT_LIMIT)
                .map(AvailableOfferView::from)
                .toList();
    }
}