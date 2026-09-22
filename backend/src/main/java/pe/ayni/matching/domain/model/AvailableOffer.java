package pe.ayni.matching.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One concrete hour a tutor has open for a course, ready to be found and booked.
 *
 * <p>Added when booking publishes {@code HoursGenerated}, one row per block and per catalog item
 * the tutor teaches. Removed when the block stops being offered ({@code HoursWithdrawn}) or gets
 * booked ({@code BookingConfirmed}). Matching never invents this data: every field here mirrors
 * what another module already decided.
 *
 * <!-- TODO: rename courseId -> catalogItemId once the team confirms the naming with skills;
 *      BookingConfirmed already uses catalogItemId. -->
 */
@Entity
@Table(schema = "matching", name = "available_offers")
public class AvailableOffer {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "tutor_id", nullable = false, updatable = false)
    private UUID tutorId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "source_hour_block_id", nullable = false, updatable = false)
    private UUID sourceHourBlockId;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Column(name = "tutor_rating")
    private BigDecimal tutorRating;

    @Column(name = "is_new_tutor", nullable = false)
    private boolean newTutor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AvailableOffer() {
        // Required by JPA
    }

    public AvailableOffer(
            UUID id,
            String tenantId,
            UUID tutorId,
            UUID courseId,
            UUID sourceHourBlockId,
            Instant startsAt,
            Instant endsAt,
            BigDecimal tutorRating,
            boolean newTutor,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
        this.courseId = Objects.requireNonNull(courseId, "courseId must not be null");
        this.sourceHourBlockId =
                Objects.requireNonNull(sourceHourBlockId, "sourceHourBlockId must not be null");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt must not be null");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt must not be null");
        this.tutorRating = tutorRating;
        this.newTutor = newTutor;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getTutorId() {
        return tutorId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getSourceHourBlockId() {
        return sourceHourBlockId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public BigDecimal getTutorRating() {
        return tutorRating;
    }

    public boolean isNewTutor() {
        return newTutor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}