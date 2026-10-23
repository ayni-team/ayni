package pe.ayni.reputation.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A tutor's reputation as the tutor reads it (US33): one entry per skill, never one figure overall.
 *
 * @param minimumAverage the average below which a skill is marked at risk
 */
public record OwnReputation(BigDecimal minimumAverage, List<SkillReputation> skills) {

    /**
     * The tutor's reputation in one skill.
     *
     * @param name {@code null} when skills no longer shows the item to this university
     * @param averageStars {@code null} below three ratings, when the tutor is new in the skill
     * @param provisionalAverage the average of the few ratings a new tutor has, for their eyes only;
     *     {@code null} once the average is shown or while nobody rated
     * @param topTags the five tags students gave most often, most frequent first
     * @param atRisk whether the shown average is below the minimum; a mark only
     */
    public record SkillReputation(
            UUID catalogItemId,
            String name,
            int sessionsTaught,
            int ratingsCount,
            BigDecimal averageStars,
            BigDecimal provisionalAverage,
            int ratingsMissingForAverage,
            List<TagTally> topTags,
            boolean atRisk) {}

    /** How many ratings carried a tag. */
    public record TagTally(String tag, long count) {}
}
