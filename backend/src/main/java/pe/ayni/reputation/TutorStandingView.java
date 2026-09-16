package pe.ayni.reputation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A tutor's standing in one skill.
 *
 * @param averageStars {@code null} below three ratings, when the tutor is shown as new
 */
public record TutorStandingView(
    UUID tutorId,
    UUID catalogItemId,
    int sessionsTaught,
    int ratingsCount,
    BigDecimal averageStars) {}
