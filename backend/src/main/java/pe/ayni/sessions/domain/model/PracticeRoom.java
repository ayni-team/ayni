package pe.ayni.sessions.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;

/**
 * A practice room: two accounts meet in the screen of a session, with its video call and its
 * whiteboard, without a booking, without credits and without leaving any record.
 *
 * <p>It is a tool of the team for trying the room, not a feature of the product. It lives only in
 * the memory of the backend and is never stored: it has no table, publishes no event and moves no
 * credit. It is forgotten when both people end it, when its lifetime is over, or when the backend
 * restarts.
 *
 * <p>The first person who enters is the tutor and the second the student; nobody else gets in. It
 * is in progress as soon as both entered, and its whiteboard is a {@link Whiteboard} kept in memory,
 * so the board follows the same rules as the board of a real session (ADR 0009). Any six digit code
 * confirms presence, since no code is sent. Not thread safe: whoever keeps the rooms holds a lock on
 * the room around every call.
 */
public final class PracticeRoom {

  /** What the room shows as the course and as the need of the student. */
  public static final String NAME = "Sala de práctica";

  private final UUID id;
  private final String tenantId;
  private final String roomName;
  private final Instant openedAt;
  private final Instant closesAt;
  private final Whiteboard board;
  private final List<Seat> seats = new ArrayList<>(2);
  private Instant startedAt;
  private Instant endedAt;

  /** One of the two people of the room, in the order they entered. */
  private static final class Seat {
    private final UUID userId;
    private final ParticipantRole role;
    private final Instant joinedAt;
    private Instant presenceConfirmedAt;
    private Instant endConfirmedAt;

    private Seat(UUID userId, ParticipantRole role, Instant joinedAt) {
      this.userId = userId;
      this.role = role;
      this.joinedAt = joinedAt;
    }
  }

  private PracticeRoom(
      UUID id, String tenantId, String roomName, Instant openedAt, Duration lifetime) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.roomName = Objects.requireNonNull(roomName, "roomName must not be null");
    this.openedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
    this.closesAt = openedAt.plus(Objects.requireNonNull(lifetime, "lifetime must not be null"));
    this.board = Whiteboard.emptyFor(tenantId, id, openedAt);
  }

  /**
   * An empty room of a university, open from {@code now} for {@code lifetime}.
   *
   * @param roomName the room of the video call, as unguessable as the one of a real session
   */
  public static PracticeRoom open(
      UUID id, String tenantId, String roomName, Instant now, Duration lifetime) {
    return new PracticeRoom(id, tenantId, roomName, now, lifetime);
  }

  /**
   * Lets the person in, as the tutor when the room is empty and as the student when the tutor is
   * already there. Entering again changes nothing.
   *
   * @return the role the person has in the room
   * @throws NotAParticipant when both seats are taken by other people
   * @throws SessionNotOpen when the room was already ended
   */
  public ParticipantRole enter(UUID userId, Instant now) {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(now, "now must not be null");
    Optional<Seat> mine = seatOf(userId);
    if (mine.isPresent()) {
      return mine.get().role;
    }
    requireNotEnded();
    if (seats.size() == 2) {
      throw new NotAParticipant();
    }
    ParticipantRole role = seats.isEmpty() ? ParticipantRole.TUTOR : ParticipantRole.STUDENT;
    seats.add(new Seat(userId, role, now));
    if (seats.size() == 2) {
      startedAt = now;
    }
    return role;
  }

  /**
   * The role the person has, or the one they would take by entering while a seat is free: the real
   * screen of a session reads it before offering to enter.
   *
   * @throws NotAParticipant when both seats are taken by other people
   */
  public ParticipantRole roleOf(UUID userId) {
    Optional<Seat> mine = seatOf(userId);
    if (mine.isPresent()) {
      return mine.get().role;
    }
    if (seats.size() == 2) {
      throw new NotAParticipant();
    }
    return seats.isEmpty() ? ParticipantRole.TUTOR : ParticipantRole.STUDENT;
  }

  /**
   * The role of somebody who entered.
   *
   * @throws NotAParticipant when the person did not enter the room
   */
  public ParticipantRole requireEntered(UUID userId) {
    return seatOf(userId).map(seat -> seat.role).orElseThrow(NotAParticipant::new);
  }

  /**
   * Confirms the presence of somebody who entered, with any six digit code: no code is sent.
   * Confirming again keeps the first moment.
   *
   * @return when the presence was first confirmed
   * @throws NotAParticipant when the person did not enter the room
   * @throws PresenceCheckUnavailable when the room is not in progress
   */
  public Instant confirmPresence(UUID userId, Instant now) {
    Seat seat = requireSeat(userId);
    if (status() != SessionStatus.IN_PROGRESS) {
      throw new PresenceCheckUnavailable(
          "The practice room is in progress once both people are in: only then can presence be"
              + " confirmed");
    }
    if (seat.presenceConfirmedAt == null) {
      seat.presenceConfirmedAt = Objects.requireNonNull(now, "now must not be null");
    }
    return seat.presenceConfirmedAt;
  }

  /**
   * Whether a code may be asked for again. Nothing is sent: the answer is the one of a real
   * session, and any six digit code is accepted anyway.
   *
   * @throws NotAParticipant when the person did not enter the room
   * @throws PresenceResendRefused when the room is not in progress or presence is confirmed
   */
  public void requireResend(UUID userId) {
    Seat seat = requireSeat(userId);
    if (status() != SessionStatus.IN_PROGRESS) {
      throw new PresenceResendRefused("The practice room is not in progress");
    }
    if (seat.presenceConfirmedAt != null) {
      throw new PresenceResendRefused("Presence is already confirmed");
    }
  }

  /**
   * Records that the person confirms the end. The second confirmation ends the room.
   *
   * @return whether this confirmation ended the room
   * @throws NotAParticipant when the person did not enter the room
   * @throws SessionNotOpen when the room is not in progress
   */
  public boolean confirmEnd(UUID userId, Instant now) {
    Seat seat = requireSeat(userId);
    requireInProgress("ended");
    if (seat.endConfirmedAt == null) {
      seat.endConfirmedAt = Objects.requireNonNull(now, "now must not be null");
    }
    if (seats.stream().allMatch(each -> each.endConfirmedAt != null)) {
      endedAt = now;
      return true;
    }
    return false;
  }

  /**
   * Who may open the live channel of the board, or draw on it: somebody who entered, while the
   * room is in progress.
   *
   * @throws NotAParticipant when the person did not enter the room
   * @throws SessionNotOpen when the room is not in progress
   */
  public void requireLive(UUID userId) {
    requireSeat(userId);
    requireInProgress("drawn on");
  }

  /**
   * Applies an operation to the board, with the rules of every board.
   *
   * @return the version this operation has
   */
  public long draw(UUID userId, BoardOperation operation, Instant now) {
    requireLive(userId);
    return board.apply(operation, now);
  }

  /**
   * Replaces every element of the board, when the person saw its current version.
   *
   * @throws WhiteboardVersionConflict when it changed after {@code seenVersion}
   */
  public void replace(UUID userId, List<BoardElement> elements, long seenVersion, Instant now) {
    requireLive(userId);
    board.replace(elements, seenVersion, now);
  }

  /**
   * The board, for somebody who entered.
   *
   * @throws NotAParticipant when the person did not enter the room
   */
  public Whiteboard boardFor(UUID userId) {
    requireSeat(userId);
    return board;
  }

  /** {@code SCHEDULED} until both entered, {@code IN_PROGRESS} then, {@code COMPLETED} once ended. */
  public SessionStatus status() {
    if (endedAt != null) {
      return SessionStatus.COMPLETED;
    }
    return seats.size() == 2 ? SessionStatus.IN_PROGRESS : SessionStatus.SCHEDULED;
  }

  /** Whether its lifetime is over at {@code now}. */
  public boolean isExpiredAt(Instant now) {
    return !now.isBefore(closesAt);
  }

  public boolean isEnded() {
    return endedAt != null;
  }

  /** The person in that seat, if somebody took it. */
  public Optional<UUID> personAs(ParticipantRole role) {
    return seats.stream().filter(seat -> seat.role == role).map(seat -> seat.userId).findFirst();
  }

  /** When the person in that seat entered, {@code null} while it is free. */
  public Instant joinedAtAs(ParticipantRole role) {
    return seats.stream()
        .filter(seat -> seat.role == role)
        .map(seat -> seat.joinedAt)
        .findFirst()
        .orElse(null);
  }

  /** When the person confirmed their presence, {@code null} if they have not or did not enter. */
  public Instant presenceConfirmedAtOf(UUID userId) {
    return seatOf(userId).map(seat -> seat.presenceConfirmedAt).orElse(null);
  }

  /** When the person confirmed the end, {@code null} if they have not or did not enter. */
  public Instant endConfirmedAtOf(UUID userId) {
    return seatOf(userId).map(seat -> seat.endConfirmedAt).orElse(null);
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getRoomName() {
    return roomName;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  /** The end of its lifetime: the room is forgotten then, ended or not. */
  public Instant getClosesAt() {
    return closesAt;
  }

  /** When both people were in, {@code null} until then. */
  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  private Optional<Seat> seatOf(UUID userId) {
    return seats.stream().filter(seat -> seat.userId.equals(userId)).findFirst();
  }

  private Seat requireSeat(UUID userId) {
    return seatOf(Objects.requireNonNull(userId, "userId must not be null"))
        .orElseThrow(NotAParticipant::new);
  }

  private void requireNotEnded() {
    if (endedAt != null) {
      throw new SessionNotOpen("This practice room was ended");
    }
  }

  private void requireInProgress(String what) {
    if (status() != SessionStatus.IN_PROGRESS) {
      throw new SessionNotOpen(
          "This practice room is "
              + status().name().toLowerCase(java.util.Locale.ROOT)
              + ": it can only be "
              + what
              + " while both people are in");
    }
  }
}
