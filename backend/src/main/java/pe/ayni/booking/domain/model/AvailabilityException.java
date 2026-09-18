package pe.ayni.booking.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a date-specific deviation from a tutor's recurring availability pattern.
 * <p>
 * An exception modifies the standard schedule for a concrete calendar date in one of two ways:
 * <ul>
 *   <li><b>REMOVE:</b> Cancels availability that would otherwise exist. When defined without
 *       specific times ({@code startsAtTime} and {@code endsAtTime} are null), it cancels the entire day.
 *       When times are provided, it subtracts only that specific time window.</li>
 *   <li><b>ADD:</b> Introduces extraordinary availability outside regular recurring hours for that date,
 *       requiring explicit start and end times.</li>
 * </ul>
 * <p>
 * During inventory generation, the booking engine applies these exceptions over base
 * recurring patterns before materializing {@code booking.hour_blocks}.
 */
@Entity
@Table(schema = "booking", name = "availability_exceptions")
public class AvailabilityException {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "tutor_id", nullable = false, updatable = false)
    private UUID tutorId;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Column(name = "starts_at_time")
    private LocalTime startsAtTime;

    @Column(name = "ends_at_time")
    private LocalTime endsAtTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 8, nullable = false)
    private ExceptionKind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    protected AvailabilityException() {
        // Required by JPA
    }

    public AvailabilityException(
            UUID id,
            String tenantId,
            UUID tutorId,
            LocalDate exceptionDate,
            LocalTime startsAtTime,
            LocalTime endsAtTime,
            ExceptionKind kind,
            Instant now
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
        this.exceptionDate = Objects.requireNonNull(exceptionDate, "exceptionDate must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.startsAtTime = startsAtTime;
        this.endsAtTime = endsAtTime;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");

        validateInvariants();
    }

    private void validateInvariants() {
        if (this.kind == ExceptionKind.ADD) {
            if (this.startsAtTime == null || this.endsAtTime == null) {
                throw new IllegalStateException("An ADD exception requires starts_at_time and ends_at_time");
            }
        }
        if (this.startsAtTime != null && this.endsAtTime != null) {
            if (!this.endsAtTime.isAfter(this.startsAtTime)) {
                throw new IllegalArgumentException("ends_at_time must be strictly after starts_at_time");
            }
        }
    }

    // Factory method for full-day removal (covers whole day without times)
    public static AvailabilityException removeWholeDay(
            UUID id,
            String tenantId,
            UUID tutorId,
            LocalDate exceptionDate,
            Instant now
    ) {
        return new AvailabilityException(
                id, tenantId, tutorId, exceptionDate, null, null, ExceptionKind.REMOVE, now
        );
    }

    // Factory method for removing a specific time window
    public static AvailabilityException removeSlot(
            UUID id,
            String tenantId,
            UUID tutorId,
            LocalDate exceptionDate,
            LocalTime startsAtTime,
            LocalTime endsAtTime,
            Instant now
    ) {
        return new AvailabilityException(
                id, tenantId, tutorId, exceptionDate, startsAtTime, endsAtTime, ExceptionKind.REMOVE, now
        );
    }

    // Factory method for adding an extra availability slot
    public static AvailabilityException addSlot(
            UUID id,
            String tenantId,
            UUID tutorId,
            LocalDate exceptionDate,
            LocalTime startsAtTime,
            LocalTime endsAtTime,
            Instant now
    ) {
        return new AvailabilityException(
                id, tenantId, tutorId, exceptionDate, startsAtTime, endsAtTime, ExceptionKind.ADD, now
        );
    }


    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getTutorId() { return tutorId; }
    public LocalDate getExceptionDate() { return exceptionDate; }
    public LocalTime getStartsAtTime() { return startsAtTime; }
    public LocalTime getEndsAtTime() { return endsAtTime; }
    public ExceptionKind getKind() { return kind; }
    public Instant getCreatedAt() { return createdAt; }

}
