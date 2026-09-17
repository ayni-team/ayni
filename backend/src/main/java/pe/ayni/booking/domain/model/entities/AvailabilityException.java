package pe.ayni.booking.domain.model.entities;

import jakarta.persistence.*;
import pe.ayni.booking.domain.model.valueobjects.ExceptionKind;
import pe.ayni.booking.domain.model.valueobjects.TimeRange;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "tutor_id", nullable = false)
    private UUID tutorId;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Embedded
    private TimeRange timeRange;

    @Enumerated(EnumType.STRING)
    @Column(length = 8, nullable = false)
    private ExceptionKind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;


    protected AvailabilityException() {
        // Required by JPA
    }

    public AvailabilityException(
            UUID id,
            String tenantId,
            UUID tutorId,
            LocalDate exceptionDate,
            TimeRange timeRange,
            ExceptionKind kind
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
        this.exceptionDate = Objects.requireNonNull(exceptionDate, "exceptionDate must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.timeRange = timeRange;
        this.createdAt = OffsetDateTime.now();

        validateInvariants();
    }

    private void validateInvariants() {
        // ADD always requires a specific time window
        if (this.kind == ExceptionKind.ADD) {
            if (this.timeRange == null || this.timeRange.startsAtTime() == null || this.timeRange.endsAtTime() == null) {
                throw new IllegalStateException("An 'ADD' exception must specify starts_at_time and ends_at_time");
            }
        }
    }

    // Factory method for full-day removal (covers whole day without times)
    public static AvailabilityException removeWholeDay(
            UUID id,
            String tenantId,
            UUID tutorId,
            LocalDate exceptionDate
    ) {
        return new AvailabilityException(id, tenantId, tutorId, exceptionDate, null, ExceptionKind.REMOVE);
    }

    // Factory method for removing a specific time window
    public static AvailabilityException removeSlot(
            UUID id,
            String tenantId,
            UUID tutorId,
            LocalDate exceptionDate,
            TimeRange timeRange
    ) {
        return new AvailabilityException(id, tenantId, tutorId, exceptionDate, timeRange, ExceptionKind.REMOVE);
    }

    // Factory method for adding an extra availability slot
    public static AvailabilityException addSlot(
            UUID id,
            String tenantId,
            UUID tutorId,
            LocalDate exceptionDate,
            TimeRange timeRange
    ) {
        return new AvailabilityException(id, tenantId, tutorId, exceptionDate, timeRange, ExceptionKind.ADD);
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

    public LocalDate getExceptionDate() {
        return exceptionDate;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public ExceptionKind getKind() {
        return kind;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }



}
