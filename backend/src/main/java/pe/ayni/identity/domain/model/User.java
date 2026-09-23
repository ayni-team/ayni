package pe.ayni.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import pe.ayni.identity.UserRole;

@Entity
@Table(schema = "identity", name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    private UserRole role;

    @Column(name = "email", length = 160, nullable = false)
    private String email;

    @Column(name = "student_code", length = 32)
    private String studentCode;

    @Column(name = "full_name", length = 160, nullable = false)
    private String fullName;

    @Column(name = "career", length = 160)
    private String career;

    @Column(name = "current_term", length = 16)
    private String currentTerm;

    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @Column(name = "bio", length = 500)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private UserStatus status;

    @Column(name = "onboarding_step", length = 32)
    private String onboardingStep;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // Required by JPA
    }

    public User(
            UUID id,
            String tenantId,
            UserRole role,
            String email,
            String studentCode,
            String fullName,
            String career,
            String currentTerm,
            String photoUrl,
            String bio,
            UserStatus status,
            String onboardingStep,
            Instant activatedAt,
            Instant createdAt,
            Instant updatedAt) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.email = normalizeEmail(email);
        this.studentCode = studentCode;
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
        this.career = career;
        this.currentTerm = currentTerm;
        this.photoUrl = photoUrl;
        this.bio = bio;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.onboardingStep = onboardingStep;
        this.activatedAt = activatedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    private static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email must not be null")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UserRole getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCareer() {
        return career;
    }

    public String getCurrentTerm() {
        return currentTerm;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getBio() {
        return bio;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getOnboardingStep() {
        return onboardingStep;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}