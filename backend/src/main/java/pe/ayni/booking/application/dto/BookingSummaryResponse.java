package pe.ayni.booking.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only DTO representing a scheduled tutoring session (US04 / US21).
 */
public class BookingSummaryResponse {

    private final UUID bookingId;
    private final String tenantId;
    private final String tutorId;
    private final String studentId;
    private final String courseName;
    private final Instant scheduledAt;
    private final String status;

    /**
     * Constructs a BookingSummaryResponse instance.
     *
     * @param bookingId   booking unique identifier
     * @param tenantId    tenant identifier
     * @param tutorId     tutor identifier
     * @param studentId   student identifier
     * @param courseName  course name
     * @param scheduledAt session start timestamp
     * @param status      booking status
     */
    public BookingSummaryResponse(UUID bookingId, String tenantId, String tutorId, String studentId, String courseName, Instant scheduledAt, String status) {
        this.bookingId = bookingId;
        this.tenantId = tenantId;
        this.tutorId = tutorId;
        this.studentId = studentId;
        this.courseName = courseName;
        this.scheduledAt = scheduledAt;
        this.status = status;
    }

    /** @return booking unique identifier */
    public UUID getBookingId() { return bookingId; }

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
    public String getStatus() { return status; }
}