package pe.ayni.reputation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
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

    public void recordCompletedSession(Instant occurredOn) {
        this.sessionsTaught++;
        this.updatedAt = occurredOn;
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