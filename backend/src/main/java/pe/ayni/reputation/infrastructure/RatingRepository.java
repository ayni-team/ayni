package pe.ayni.reputation.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.reputation.domain.model.Rating;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    boolean existsByTenantIdAndSessionIdAndDirection(
            String tenantId,
            UUID sessionId,
            Rating.Direction direction);

    /**
     * The rating of one session in one direction, with its tags in the same query. The unique key
     * of the table, the session and the direction, finds it.
     */
    @EntityGraph(attributePaths = "tags")
    Optional<Rating> findByTenantIdAndSessionIdAndDirection(
            String tenantId,
            UUID sessionId,
            Rating.Direction direction);

    /**
     * How many times students gave each tag to the tutor, per skill, in one query. The skill comes
     * from the window of the session, since a rating has no column for it. Nothing about who rated
     * leaves this query.
     */
    @Query(
            "select new pe.ayni.reputation.infrastructure.TagCount("
                    + "window.catalogItemId, tag, count(rating))"
                    + " from Rating rating"
                    + " join rating.tags tag"
                    + " join RatingWindow window"
                    + " on window.tenantId = rating.tenantId and window.sessionId = rating.sessionId"
                    + " where rating.tenantId = :tenantId"
                    + " and rating.ratedUser = :tutorId"
                    + " and rating.direction = :direction"
                    + " group by window.catalogItemId, tag")
    List<TagCount> countTagsOfRatedUser(
            @Param("tenantId") String tenantId,
            @Param("tutorId") UUID tutorId,
            @Param("direction") Rating.Direction direction);
}
