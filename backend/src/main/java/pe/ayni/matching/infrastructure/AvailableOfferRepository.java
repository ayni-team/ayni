package pe.ayni.matching.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.matching.domain.model.AvailableOffer;

public interface AvailableOfferRepository extends JpaRepository<AvailableOffer, UUID> {

    /**
     * Removes every offer generated from the given hour blocks, within one tenant.
     *
     * <p>Used both when a tutor withdraws hours and when an hour gets booked: in both cases the
     * block stops being searchable and every offer built from it (one per catalog item the tutor
     * teaches) must go with it.
     */
    long deleteByTenantIdAndSourceHourBlockIdIn(String tenantId, List<UUID> sourceHourBlockIds);

    /**
     * Offers for a course that start within [from, to), soonest first, best-rated tutor first among
     * ties on the same hour.
     */
    List<AvailableOffer> findByTenantIdAndCourseIdAndStartsAtBetweenOrderByStartsAtAscTutorRatingDesc(
            String tenantId, UUID courseId, Instant from, Instant to);

    /** Up to 10 offers at or after the given instant, soonest first: the fallback's "after" side. */
    List<AvailableOffer> findTop10ByTenantIdAndCourseIdAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
            String tenantId, UUID courseId, Instant from);

    /** Up to 10 offers strictly before the given instant, latest first: the fallback's "before" side. */
    List<AvailableOffer> findTop10ByTenantIdAndCourseIdAndStartsAtLessThanOrderByStartsAtDesc(
            String tenantId, UUID courseId, Instant before);
}