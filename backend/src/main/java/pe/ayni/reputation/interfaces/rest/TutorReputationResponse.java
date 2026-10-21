package pe.ayni.reputation.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import pe.ayni.reputation.application.OwnReputation;

/** The caller's own reputation as a tutor, one entry per skill. Nobody who rated is named. */
@Schema(name = "TutorReputation", description = "The caller's reputation as a tutor, per skill")
record TutorReputationResponse(
        @Schema(description = "Average below which a skill is marked at risk", example = "3.0")
                BigDecimal minimumAverage,
        List<Skill> skills) {

    @Schema(name = "TutorSkillReputation", description = "The caller's reputation in one skill")
    record Skill(
            @Schema(example = "22222222-2222-4222-8222-222222222222") UUID catalogItemId,
            @Schema(description = "Name of the catalogue item", example = "Calculus I") String name,
            @Schema(example = "8") int sessionsTaught,
            @Schema(example = "5") int ratingsCount,
            @Schema(
                            description = "Average of the ratings; null below three ratings",
                            example = "4.60",
                            nullable = true)
                    BigDecimal averageStars,
            @Schema(
                            description =
                                    "Average of the few ratings so far, below three ratings and for"
                                            + " the tutor only; null otherwise",
                            example = "null",
                            nullable = true)
                    BigDecimal provisionalAverage,
            @Schema(description = "Ratings still needed before the average is shown", example = "0")
                    int ratingsMissingForAverage,
            @Schema(description = "Up to five tags students gave most, most frequent first")
                    List<TagTally> topTags,
            @Schema(
                            description =
                                    "Whether the average is below the minimum. A mark only: no skill"
                                            + " is withdrawn and no notice is sent",
                            example = "false")
                    boolean atRisk) {}

    @Schema(name = "TutorTagCount", description = "How many ratings carried a tag")
    record TagTally(@Schema(example = "CLEAR") String tag, @Schema(example = "4") long count) {}

    static TutorReputationResponse from(OwnReputation reputation) {
        return new TutorReputationResponse(
                reputation.minimumAverage(),
                reputation.skills().stream()
                        .map(
                                skill ->
                                        new Skill(
                                                skill.catalogItemId(),
                                                skill.name(),
                                                skill.sessionsTaught(),
                                                skill.ratingsCount(),
                                                skill.averageStars(),
                                                skill.provisionalAverage(),
                                                skill.ratingsMissingForAverage(),
                                                skill.topTags().stream()
                                                        .map(tag -> new TagTally(tag.tag(), tag.count()))
                                                        .toList(),
                                                skill.atRisk()))
                        .toList());
    }
}
