package pe.ayni.reputation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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