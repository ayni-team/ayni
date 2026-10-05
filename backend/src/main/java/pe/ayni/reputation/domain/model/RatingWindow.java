package pe.ayni.reputation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@IdClass(RatingWindowId.class)
@Table(schema = "reputation", name = "rating_windows")
public class RatingWindow {

    @Id
    @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
    private String tenantId;

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "tutor_id", nullable = false, updatable = false)
    private UUID tutorId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "catalog_item_id", nullable = false, updatable = false)
    private UUID catalogItemId;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    protected RatingWindow() {
        // Required by JPA
    }

    public RatingWindow(
            String tenantId,
            UUID sessionId,
            UUID tutorId,
            UUID studentId,
            UUID catalogItemId,
            Instant openedAt) {
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.tutorId = tutorId;
        this.studentId = studentId;
        this.catalogItemId = catalogItemId;
        this.openedAt = openedAt;
    }

    public String tenantId() {
        return tenantId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID tutorId() {
        return tutorId;
    }

    public UUID studentId() {
        return studentId;
    }

    public UUID catalogItemId() {
        return catalogItemId;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public boolean isParticipant(UUID userId) {
        return tutorId.equals(userId) || studentId.equals(userId);
    }
}