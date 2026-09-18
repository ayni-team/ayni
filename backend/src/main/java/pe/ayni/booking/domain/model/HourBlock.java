package pe.ayni.booking.domain.model;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root representing an atomic one-hour unit of tutor availability.
 * <p>
 * Manages hold reservations, final bookings, optimistic locking, and release lifecycles.
 */
@Entity
@Table(
        name = "hour_blocks",
        schema = "booking",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_hour_blocks_tenant_tutor_start",
                        columnNames = {"tenant_id", "tutor_id", "starts_at"}
                )
        }
)
public class HourBlock {

    public static final Duration DEFAULT_HOLD_DURATION = Duration.ofMinutes(15);

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "tutor_id", nullable = false, updatable = false)
    private UUID tutorId;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private HourBlockStatus status;

    @Column(name = "held_by")
    private UUID heldBy;

    @Column(name = "held_until")
    private Instant heldUntil;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "pattern_id")
    private UUID patternId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HourBlock() {
        // Required by JPA
    }

    public HourBlock(
            UUID id,
            String tenantId,
            UUID tutorId,
            Instant startsAt,
            Instant endsAt,
            UUID patternId,
            Instant now
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt must not be null");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt must not be null");
        this.patternId = patternId;
        this.status = HourBlockStatus.AVAILABLE;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");

        validateInvariants();
    }

    private void validateInvariants() {
        if (!this.endsAt.isAfter(this.startsAt)) {
            throw new IllegalArgumentException("ends_at must be strictly after starts_at");
        }
    }

    /**
     * Temporarily reserves the block for a student while they complete booking details.
     */
    public void hold(UUID studentId, Instant now) {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (this.status != HourBlockStatus.AVAILABLE && !isHoldExpired(now)) {
            throw new IllegalStateException("Cannot hold block because it is currently " + this.status);
        }

        this.status = HourBlockStatus.HELD;
        this.heldBy = studentId;
        this.heldUntil = now.plus(DEFAULT_HOLD_DURATION);
    }

    /**
     * Releases an expired or abandoned hold back into circulation.
     */
    public void releaseHold(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        if (this.status != HourBlockStatus.HELD) {
            return;
        }

        this.status = HourBlockStatus.AVAILABLE;
        this.heldBy = null;
        this.heldUntil = null;
    }

    /**
     * Confirms the booking, taking the block out of available inventory.
     */
    public void book(UUID bookingId) {
        Objects.requireNonNull(bookingId, "bookingId must not be null");

        if (this.status != HourBlockStatus.HELD && this.status != HourBlockStatus.AVAILABLE) {
            throw new IllegalStateException("Block cannot be booked from status: " + this.status);
        }

        this.status = HourBlockStatus.BOOKED;
        this.bookingId = bookingId;
        this.heldBy = null;
        this.heldUntil = null;
    }

    /**
     * Releases a previously booked block (e.g. following a cancellation).
     */
    public void release() {
        if (this.status != HourBlockStatus.BOOKED) {
            throw new IllegalStateException("Only booked blocks can be released");
        }

        this.status = HourBlockStatus.RELEASED;
        this.bookingId = null;
        this.heldBy = null;
        this.heldUntil = null;
    }

    public boolean isHoldExpired(Instant now) {
        return this.status == HourBlockStatus.HELD && this.heldUntil != null && now.isAfter(this.heldUntil);
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getTutorId() { return tutorId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public HourBlockStatus getStatus() { return status; }
    public UUID getHeldBy() { return heldBy; }
    public Instant getHeldUntil() { return heldUntil; }
    public UUID getBookingId() { return bookingId; }
    public UUID getPatternId() { return patternId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
