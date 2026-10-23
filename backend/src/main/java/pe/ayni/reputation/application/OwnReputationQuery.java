package pe.ayni.reputation.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.reputation.application.OwnReputation.SkillReputation;
import pe.ayni.reputation.application.OwnReputation.TagTally;
import pe.ayni.reputation.domain.model.Rating;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.infrastructure.RatingRepository;
import pe.ayni.reputation.infrastructure.TagCount;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.SkillsApi;

/**
 * US33: the tutor's own reputation, one entry per skill in which they have a standing.
 *
 * <p>A skill has a standing when it already has a row in {@code reputation.tutor_standing}, because
 * the tutor taught it, or when skills says the tutor is enabled for it, in which case nothing is
 * counted yet. A skill the tutor was withdrawn from keeps the standing they earned.
 *
 * <p>Two queries of this module answer it whatever the number of skills: the standings by the
 * tutor's part of their primary key, and the tags counted per skill in the database. Only counts
 * leave them, never who rated. The name of each item is asked of skills.
 *
 * <p>Being at risk is a mark to read and nothing more: this query withdraws no skill, publishes no
 * event and sends no notice.
 */
@Service
public class OwnReputationQuery {

    /** How many of the most frequent tags each skill shows. */
    static final int TOP_TAGS = 5;

    private final TutorStandingRepository standings;
    private final RatingRepository ratings;
    private final SkillsApi skills;
    private final Clock clock;
    private final BigDecimal minimumAverage;

    OwnReputationQuery(
            TutorStandingRepository standings,
            RatingRepository ratings,
            SkillsApi skills,
            Clock clock,
            @Value("${ayni.reputation.minimum-average:3.0}") BigDecimal minimumAverage) {

        if (minimumAverage.compareTo(BigDecimal.ONE) < 0
                || minimumAverage.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new IllegalArgumentException(
                    "ayni.reputation.minimum-average must be between 1 and 5");
        }
        this.standings = standings;
        this.ratings = ratings;
        this.skills = skills;
        this.clock = clock;
        this.minimumAverage = minimumAverage;
    }

    @Transactional(readOnly = true)
    public OwnReputation execute(UUID tutorId) {
        Objects.requireNonNull(tutorId, "tutorId must not be null");
        String tenantId = TenantContext.require();

        Map<UUID, TutorStanding> bySkill = new LinkedHashMap<>();
        for (TutorStanding standing : standings.findByTenantIdAndTutorId(tenantId, tutorId)) {
            bySkill.put(standing.catalogItemId(), standing);
        }
        for (UUID enabled : skills.enabledSkillsOf(tutorId)) {
            bySkill.computeIfAbsent(
                    enabled,
                    item -> TutorStanding.initial(tenantId, tutorId, item, clock.instant()));
        }

        Map<UUID, List<TagTally>> tagsBySkill =
                topTagsBySkill(
                        ratings.countTagsOfRatedUser(
                                tenantId, tutorId, Rating.Direction.STUDENT_TO_TUTOR));

        List<SkillReputation> entries =
                bySkill.values().stream()
                        .map(
                                standing ->
                                        new SkillReputation(
                                                standing.catalogItemId(),
                                                nameOf(standing.catalogItemId()),
                                                standing.sessionsTaught(),
                                                standing.ratingsCount(),
                                                standing.visibleAverageStars(),
                                                standing.provisionalAverageStars(),
                                                standing.ratingsMissingForAverage(),
                                                tagsBySkill.getOrDefault(
                                                        standing.catalogItemId(), List.of()),
                                                standing.isAtRisk(minimumAverage)))
                        .sorted(
                                Comparator.comparing(
                                                SkillReputation::name,
                                                Comparator.nullsLast(
                                                        String.CASE_INSENSITIVE_ORDER))
                                        .thenComparing(SkillReputation::catalogItemId))
                        .toList();

        return new OwnReputation(minimumAverage, entries);
    }

    private static Map<UUID, List<TagTally>> topTagsBySkill(List<TagCount> counts) {
        return counts.stream()
                .collect(
                        Collectors.groupingBy(
                                TagCount::catalogItemId,
                                Collectors.collectingAndThen(
                                        Collectors.toList(), OwnReputationQuery::mostFrequent)));
    }

    /** The most frequent tags first, and among equally frequent ones alphabetical order. */
    private static List<TagTally> mostFrequent(List<TagCount> perSkill) {
        Comparator<TagCount> mostFrequentFirst =
                Comparator.comparing(TagCount::times, Comparator.reverseOrder())
                        .thenComparing(TagCount::tag);
        return perSkill.stream()
                .sorted(mostFrequentFirst)
                .limit(TOP_TAGS)
                .map(count -> new TagTally(count.tag(), count.times()))
                .toList();
    }

    /**
     * The name skills gives the item. An item skills no longer shows to this university does not
     * hide the standing the tutor earned in it; it travels without a name.
     */
    private String nameOf(UUID catalogItemId) {
        try {
            return skills.requireItem(catalogItemId).name();
        } catch (NoSuchElementException notVisible) {
            return null;
        }
    }
}
