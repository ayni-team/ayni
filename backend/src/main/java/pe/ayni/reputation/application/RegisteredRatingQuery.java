package pe.ayni.reputation.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.reputation.domain.model.NotAParticipant;
import pe.ayni.reputation.domain.model.Rating;
import pe.ayni.reputation.domain.model.RatingWindow;
import pe.ayni.reputation.domain.model.SessionNotRatable;
import pe.ayni.reputation.infrastructure.RatingRepository;
import pe.ayni.reputation.infrastructure.RatingWindowRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US32 and US34: what the caller registered about a session, so the client shows it instead of
 * offering to rate again.
 *
 * <p>The direction read is always the caller's own, decided by {@link
 * RatingWindow#directionRatedBy}. The tutor therefore never reads the student's rating of the
 * session, which would tell them who left it, and the student never reads the tutor's assessment
 * of them.
 */
@Service
public class RegisteredRatingQuery {

    private final RatingWindowRepository ratingWindows;
    private final RatingRepository ratings;

    RegisteredRatingQuery(RatingWindowRepository ratingWindows, RatingRepository ratings) {
        this.ratingWindows = ratingWindows;
        this.ratings = ratings;
    }

    /**
     * @throws SessionNotRatable when there is no completed session with that id in the university
     * @throws NotAParticipant when the caller was neither the tutor nor the student of the session
     */
    @Transactional(readOnly = true)
    public RegisteredRating execute(UUID sessionId, UUID userId) {
        String tenantId = TenantContext.require();

        RatingWindow window =
                ratingWindows
                        .findByTenantIdAndSessionId(tenantId, sessionId)
                        .orElseThrow(SessionNotRatable::new);

        Rating.Direction direction = window.directionRatedBy(userId);

        return ratings
                .findByTenantIdAndSessionIdAndDirection(tenantId, sessionId, direction)
                .map(RegisteredRatingQuery::registered)
                .orElseGet(RegisteredRating::none);
    }

    private static RegisteredRating registered(Rating rating) {
        return switch (rating.direction()) {
            case STUDENT_TO_TUTOR ->
                    new RegisteredRating(
                            true,
                            rating.stars(),
                            rating.tags().stream().sorted().toList(),
                            null,
                            null,
                            null,
                            rating.comment(),
                            rating.createdAt());
            case TUTOR_TO_STUDENT ->
                    new RegisteredRating(
                            true,
                            null,
                            null,
                            rating.wasPunctual(),
                            rating.connectionOk(),
                            rating.sessionFlowed(),
                            rating.comment(),
                            rating.createdAt());
        };
    }
}
