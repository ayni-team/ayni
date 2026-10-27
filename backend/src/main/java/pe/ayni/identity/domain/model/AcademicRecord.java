package pe.ayni.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "academic_records")
public class AcademicRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "course_code", length = 32, nullable = false)
    private String courseCode;

    @Column(name = "course_name", length = 160, nullable = false)
    private String courseName;

    @Column(name = "grade", precision = 4, scale = 2, nullable = false)
    private BigDecimal grade;

    @Column(name = "term", length = 16, nullable = false)
    private String term;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected AcademicRecord() {
        // Required by JPA
    }

    public AcademicRecord(
            UUID id,
            String tenantId,
            UUID userId,
            String courseCode,
            String courseName,
            BigDecimal grade,
            String term,
            Instant syncedAt) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.courseCode = Objects.requireNonNull(courseCode, "courseCode must not be null");
        this.courseName = Objects.requireNonNull(courseName, "courseName must not be null");
        this.grade = Objects.requireNonNull(grade, "grade must not be null");
        this.term = Objects.requireNonNull(term, "term must not be null");
        this.syncedAt = Objects.requireNonNull(syncedAt, "syncedAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public BigDecimal getGrade() {
        return grade;
    }

    public String getTerm() {
        return term;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}