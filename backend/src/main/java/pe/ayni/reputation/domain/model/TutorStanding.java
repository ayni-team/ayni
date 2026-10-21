package pe.ayni.reputation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;


/**
 * A tutor's standing in one skill: a projection rebuilt from completed sessions and ratings.
 *
 * <p>Per skill and never overall, because being good at Calculus says nothing about teaching
 * Photoshop. Below {@link #RATINGS_BEFORE_AVERAGE} ratings the average is not shown and the tutor
 * appears as new: two opinions are not a reputation.
 */
@Entity
@IdClass(TutorStandingId.class)
@Table(schema = "reputation", name = "tutor_standing")
public class TutorStanding {

    @Id
    @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
    private String tenantId;

    @Id
    @Column(name = "tutor_id", nullable = false, updatable = false)
    private UUID tutorId;

    @Id
    @Column(name = "catalog_item_id", nullable = false, updatable = false)
    private UUID catalogItemId;

    @Column(name = "sessions_taught", nullable = false)
    private int sessionsTaught;

    @Column(name = "ratings_count", nullable = false)
    private int ratingsCount;

    @Column(name = "average_stars", precision = 3, scale = 2)
    private BigDecimal averageStars;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TutorStanding() {
        // Required by JPA
    }

    public static TutorStanding initial(
            String tenantId,
            UUID tutorId,
            UUID catalogItemId,
            Instant occurredOn) {

        return new TutorStanding(
                tenantId,
                tutorId,
                catalogItemId,
                0,
                0,
                null,
                occurredOn);
    }

    /** Ratings a tutor needs in a skill before their average is shown. */
    public static final int RATINGS_BEFORE_AVERAGE = 3;

    /** Whether the tutor still has too few ratings in this skill to show an average. */
    public boolean isNew() {
        return ratingsCount < RATINGS_BEFORE_AVERAGE;
    }

    /** The average others may see: {@code null} while the tutor {@linkplain #isNew() is new}. */
    public BigDecimal visibleAverageStars() {
        return isNew() ? null : averageStars;
    }

    /** How many more ratings the tutor needs in this skill before an average is shown. */
    public int ratingsMissingForAverage() {
        return Math.max(0, RATINGS_BEFORE_AVERAGE - ratingsCount);
    }

    /**
     * The average of the few ratings a new tutor has so far, which only the tutor sees: {@code
     * null} once the average is shown, and while nobody rated yet.
     */
    public BigDecimal provisionalAverageStars() {
        return isNew() && ratingsCount > 0 ? averageStars : null;
    }

    /**
     * Whether the shown average is below the minimum the university expects. A new tutor is never
     * at risk: without three ratings there is no average to fall short. It is only a mark to read;
     * it withdraws no skill and notifies nobody.
     */
    public boolean isAtRisk(BigDecimal minimumAverage) {
        BigDecimal average = visibleAverageStars();
        return average != null && average.compareTo(minimumAverage) < 0;
    }

    public void recordCompletedSession(Instant occurredOn) {
        this.sessionsTaught++;
        this.updatedAt = occurredOn;

    }
    public void recordRating(int stars, Instant occurredOn) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Stars must be between 1 and 5");
        }

        BigDecimal previousTotal =
                averageStars == null
                        ? BigDecimal.ZERO
                        : averageStars.multiply(BigDecimal.valueOf(ratingsCount));

        ratingsCount++;

        averageStars =
                previousTotal
                        .add(BigDecimal.valueOf(stars))
                        .divide(BigDecimal.valueOf(ratingsCount), 2, RoundingMode.HALF_UP);

        updatedAt = occurredOn;
    }

    public TutorStanding(
            String tenantId,
            UUID tutorId,
            UUID catalogItemId,
            int sessionsTaught,
            int ratingsCount,
            BigDecimal averageStars,
            Instant updatedAt) {
        this.tenantId = tenantId;
        this.tutorId = tutorId;
        this.catalogItemId = catalogItemId;
        this.sessionsTaught = sessionsTaught;
        this.ratingsCount = ratingsCount;
        this.averageStars = averageStars;
        this.updatedAt = updatedAt;
    }

    public String tenantId() {
        return tenantId;
    }

    public UUID tutorId() {
        return tutorId;
    }

    public UUID catalogItemId() {
        return catalogItemId;
    }

    public int sessionsTaught() {
        return sessionsTaught;
    }

    public int ratingsCount() {
        return ratingsCount;
    }

    public BigDecimal averageStars() {
        return averageStars;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}