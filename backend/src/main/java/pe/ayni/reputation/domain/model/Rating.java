package pe.ayni.reputation.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(schema = "reputation", name = "ratings")
public class Rating {

    public enum Direction {
        STUDENT_TO_TUTOR,
        TUTOR_TO_STUDENT
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "rated_by", nullable = false, updatable = false)
    private UUID ratedBy;

    @Column(name = "rated_user", nullable = false, updatable = false)
    private UUID ratedUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 24, nullable = false, updatable = false)
    private Direction direction;

    @Column(name = "stars")
    private Short stars;

    @Column(name = "was_punctual")
    private Boolean wasPunctual;

    @Column(name = "connection_ok")
    private Boolean connectionOk;

    @Column(name = "session_flowed")
    private Boolean sessionFlowed;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            schema = "reputation",
            name = "rating_tags",
            joinColumns = @JoinColumn(name = "rating_id"))
    @Column(name = "tag", length = 40, nullable = false)
    private Set<String> tags = new LinkedHashSet<>();

    protected Rating() {
        // Required by JPA
    }

    public static Rating studentToTutor(
            UUID id,
            String tenantId,
            UUID sessionId,
            UUID studentId,
            UUID tutorId,
            int stars,
            Set<String> tags,
            String comment,
            Instant createdAt) {

        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Stars must be between 1 and 5");
        }

        return new Rating(
                id,
                tenantId,
                sessionId,
                studentId,
                tutorId,
                Direction.STUDENT_TO_TUTOR,
                stars,
                null,
                null,
                null,
                comment,
                createdAt,
                tags == null ? Set.of() : tags);
    }

    public static Rating tutorToStudent(
            UUID id,
            String tenantId,
            UUID sessionId,
            UUID tutorId,
            UUID studentId,
            boolean wasPunctual,
            boolean connectionOk,
            boolean sessionFlowed,
            String comment,
            Instant createdAt) {

        return new Rating(
                id,
                tenantId,
                sessionId,
                tutorId,
                studentId,
                Direction.TUTOR_TO_STUDENT,
                null,
                wasPunctual,
                connectionOk,
                sessionFlowed,
                comment,
                createdAt,
                Set.of());
    }

    private Rating(
            UUID id,
            String tenantId,
            UUID sessionId,
            UUID ratedBy,
            UUID ratedUser,
            Direction direction,
            Integer stars,
            Boolean wasPunctual,
            Boolean connectionOk,
            Boolean sessionFlowed,
            String comment,
            Instant createdAt,
            Set<String> tags) {

        this.id = id;
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.ratedBy = ratedBy;
        this.ratedUser = ratedUser;
        this.direction = direction;
        this.stars = stars == null ? null : stars.shortValue();
        this.wasPunctual = wasPunctual;
        this.connectionOk = connectionOk;
        this.sessionFlowed = sessionFlowed;
        this.comment = comment;
        this.createdAt = createdAt;
        this.tags = new LinkedHashSet<>(tags);
    }

    public UUID id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID ratedBy() {
        return ratedBy;
    }

    public UUID ratedUser() {
        return ratedUser;
    }

    public Direction direction() {
        return direction;
    }

    public Integer stars() {
        return stars == null ? null : stars.intValue();
    }

    public Boolean wasPunctual() {
        return wasPunctual;
    }

    public Boolean connectionOk() {
        return connectionOk;
    }

    public Boolean sessionFlowed() {
        return sessionFlowed;
    }

    public String comment() {
        return comment;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Set<String> tags() {
        return Set.copyOf(tags);
    }
}