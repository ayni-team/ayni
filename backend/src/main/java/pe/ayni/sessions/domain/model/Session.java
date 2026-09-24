package pe.ayni.sessions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;

/**
 * The live meeting a confirmed booking turns into.
 *
 * <p>It is born scheduled, when booking announces the confirmation, and keeps only the booking's
 * id: what the student needs help with belongs to the booking and is read from there.
 */
@Entity
@Table(schema = "sessions", name = "sessions")
public class Session {

  private static final SecureRandom RANDOM = new SecureRandom();

  /** 128 random bits: nobody finds a room by trying names. */
  private static final int ROOM_NAME_BYTES = 16;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "booking_id", nullable = false, updatable = false)
  private UUID bookingId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "scheduled_start", nullable = false)
  private Instant scheduledStart;

  @Column(name = "scheduled_end", nullable = false)
  private Instant scheduledEnd;

  @Column(name = "room_name", length = 120, nullable = false, updatable = false)
  private String roomName;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 24, nullable = false)
  private SessionStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Session() {
    // Required by JPA
  }

  /**
   * The session of a booking just confirmed, with a room of its own nobody can guess.
   *
   * @throws SessionRuleViolation when the session would end before it starts
   */
  public static Session schedule(
      UUID id,
      String tenantId,
      UUID bookingId,
      UUID studentId,
      UUID tutorId,
      Instant scheduledStart,
      Instant scheduledEnd,
      Instant now) {
    Session session = new Session();
    session.id = Objects.requireNonNull(id, "id must not be null");
    session.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    session.bookingId = Objects.requireNonNull(bookingId, "bookingId must not be null");
    session.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
    session.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
    session.scheduledStart = Objects.requireNonNull(scheduledStart, "scheduledStart must not be null");
    session.scheduledEnd = Objects.requireNonNull(scheduledEnd, "scheduledEnd must not be null");
    session.createdAt = Objects.requireNonNull(now, "now must not be null");

    if (!scheduledEnd.isAfter(scheduledStart)) {
      throw new SessionRuleViolation("A session must end after it starts");
    }

    session.roomName = unguessableRoomName();
    session.status = SessionStatus.SCHEDULED;
    return session;
  }

  private static String unguessableRoomName() {
    byte[] bytes = new byte[ROOM_NAME_BYTES];
    RANDOM.nextBytes(bytes);
    return "ayni-" + HexFormat.of().formatHex(bytes);
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getBookingId() {
    return bookingId;
  }

  public UUID getStudentId() {
    return studentId;
  }

  public UUID getTutorId() {
    return tutorId;
  }

  public Instant getScheduledStart() {
    return scheduledStart;
  }

  public Instant getScheduledEnd() {
    return scheduledEnd;
  }

  public String getRoomName() {
    return roomName;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public SessionStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
