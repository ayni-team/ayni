package pe.ayni.booking.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a scheduled tutoring booking session (US04 / US21).
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "tutor_id", nullable = false)
    private String tutorId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "course_name", nullable = false)
    private String courseName;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    /**
     * Enum for booking status.
     */
    public enum BookingStatus {
        CONFIRMED,
        CANCELLED,
        COMPLETED
    }

    /** Protected constructor for JPA. */
    protected Booking() {}

    /**
     * Constructs a new Booking instance.
     *
     * @param tenantId    tenant identifier
     * @param tutorId     tutor identifier
     * @param studentId   student identifier
     * @param courseName  course name
     * @param scheduledAt session timestamp
     * @param status      booking status
     */
    public Booking(String tenantId, String tutorId, String studentId, String courseName, Instant scheduledAt, BookingStatus status) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.tutorId = tutorId;
        this.studentId = studentId;
        this.courseName = courseName;
        this.scheduledAt = scheduledAt;
        this.status = status;
    }

    /** @return booking identifier */
    public UUID getId() { return id; }

    /** @return tenant identifier */
    public String getTenantId() { return tenantId; }

    /** @return tutor identifier */
    public String getTutorId() { return tutorId; }

    /** @return student identifier */
    public String getStudentId() { return studentId; }

    /** @return course name */
    public String getCourseName() { return courseName; }

    /** @return scheduled timestamp */
    public Instant getScheduledAt() { return scheduledAt; }

    /** @return status */
    public BookingStatus getStatus() { return status; }
}