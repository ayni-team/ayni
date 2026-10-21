package pe.ayni.reputation.application;

import java.time.Instant;
import java.util.List;

/**
 * What the caller left about one session, in their own direction only.
 *
 * <p>A student's rating carries {@code stars}, {@code tags} and {@code comment}; a tutor's
 * assessment carries the three facts and {@code comment}. The fields of the other direction stay
 * {@code null}. Nothing here says who was rated or by whom: the caller is the one who rated.
 *
 * @param rated whether the caller already rated the session; when {@code false} every other field
 *     is {@code null}
 */
public record RegisteredRating(
        boolean rated,
        Integer stars,
        List<String> tags,
        Boolean wasPunctual,
        Boolean connectionOk,
        Boolean sessionFlowed,
        String comment,
        Instant createdAt) {

    static RegisteredRating none() {
        return new RegisteredRating(false, null, null, null, null, null, null, null);
    }
}
