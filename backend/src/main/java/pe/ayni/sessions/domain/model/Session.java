package pe.ayni.sessions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
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

  /**
   * How early the room opens: fifteen minutes before the start, as the backend guide says, so both
   * can check their camera and connection without eating into the hour they paid for.
   */
  public static final Duration JOIN_OPENS_BEFORE = Duration.ofMinutes(15);

  /**
   * When presence codes are sent, counted from the scheduled start rather than from the first
   * arrival: somebody joining fifteen minutes early does not move the check before the hour that
   * was booked (US54).
   */
  public static final Duration PRESENCE_CHECK_AFTER = Duration.ofMinutes(5);

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

  /**
   * Which participant this person is.
   *
   * @throws NotAParticipant when they are neither the student nor the tutor
   */
  public ParticipantRole roleOf(UUID userId) {
    Objects.requireNonNull(userId, "userId must not be null");
    if (userId.equals(this.tutorId)) {
      return ParticipantRole.TUTOR;
    }
    if (userId.equals(this.studentId)) {
      return ParticipantRole.STUDENT;
    }
    throw new NotAParticipant();
  }

  /**
   * Lets a participant into the room.
   *
   * <p>The room opens {@link #JOIN_OPENS_BEFORE} before the start and closes at the scheduled end.
   * The first participant to join starts the session, whoever it is: from then on it is in
   * progress, and the start is when somebody actually arrived, not when it was scheduled. Joining a
   * session already in progress changes nothing, so coming back after a dropped connection works.
   *
   * @return whether this join started the session
   * @throws NotAParticipant when the person is neither the student nor the tutor
   * @throws SessionNotOpen when it is too early, the session is over, or it will not take place
   */
  public boolean join(UUID userId, Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    roleOf(userId);

    if (this.status != SessionStatus.SCHEDULED && this.status != SessionStatus.IN_PROGRESS) {
      throw new SessionNotOpen("This session is " + this.status.name().toLowerCase(Locale.ROOT)
          + " and can no longer be joined");
    }
    if (now.isBefore(joinOpensAt())) {
      throw new SessionNotOpen(
          "The room opens fifteen minutes before the session starts, at " + joinOpensAt());
    }
    if (!now.isBefore(this.scheduledEnd)) {
      throw new SessionNotOpen("This session has already ended");
    }

    if (this.status == SessionStatus.IN_PROGRESS) {
      return false;
    }
    this.status = SessionStatus.IN_PROGRESS;
    this.startedAt = now;
    return true;
  }

  /** The first moment a participant may join. */
  public Instant joinOpensAt() {
    return this.scheduledStart.minus(JOIN_OPENS_BEFORE);
  }

  /** When the presence codes are due. */
  public Instant presenceCheckAt() {
    return this.scheduledStart.plus(PRESENCE_CHECK_AFTER);
  }

  /**
   * Whether the presence codes should go out now.
   *
   * <p>Only while the session is in progress: one nobody joined has nobody to check yet, and the
   * codes go out as soon as somebody arrives, however late. Never after the scheduled end, when there
   * is no session left to be present in.
   */
  public boolean isDueForPresenceCheck(Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    return this.status == SessionStatus.IN_PROGRESS
        && !now.isBefore(presenceCheckAt())
        && now.isBefore(this.scheduledEnd);
  }

  /**
   * Refuses a presence confirmation when the session is not in progress.
   *
   * @throws PresenceCheckUnavailable when the session has not started or is already over
   */
  public void requireInProgressForPresence() {
    if (this.status != SessionStatus.IN_PROGRESS) {
      throw new PresenceCheckUnavailable("This session is "
          + this.status.name().toLowerCase(Locale.ROOT)
          + ": presence is confirmed while it is in progress");
    }
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
