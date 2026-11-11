package pe.ayni.sessions.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.ayni.identity.IdentityApi;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.BoardContent;
import pe.ayni.sessions.domain.model.BoardElement;
import pe.ayni.sessions.domain.model.BoardOperation;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.PracticeRoom;
import pe.ayni.sessions.domain.model.PracticeRoomsFull;
import pe.ayni.sessions.domain.model.PresenceCheck;
import pe.ayni.sessions.domain.model.Whiteboard;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The practice rooms open in this backend: a way for two accounts to try the screen of a session,
 * its video call and its whiteboard, at any time and without leaving a trace.
 *
 * <p>Everything is kept in the memory of this instance, and nothing else is touched: no booking, no
 * session, no row of any table, no credit, no event, no email. The endpoints of a real session
 * answer from here when the id is the one of an open room of the reader's university, and exactly
 * as always otherwise ({@code SessionsController}, {@link WhiteboardEditing}). A room is forgotten
 * when both people end it, when its lifetime is over ({@code ayni.sessions.practice-rooms.lifetime})
 * or when the backend restarts.
 *
 * <p>Off unless {@code ayni.sessions.practice-rooms.enabled} is true ({@code AYNI_PRACTICE_ROOMS}),
 * which the {@code demo} profile does. Off, nothing can be opened, so no id is ever a room.
 *
 * <p>No transaction here on purpose: there is nothing to store. The only reads outside this class
 * are the name of the other person, through {@link IdentityApi}, and the pass to the video call,
 * through {@link VideoRoomPasses}, which signs a token and stores nothing.
 */
@Service
public class PracticeRooms {

  private static final Logger log = LoggerFactory.getLogger(PracticeRooms.class);

  private static final SessionCourse COURSE = new SessionCourse(null, PracticeRoom.NAME, null);

  private final Map<UUID, PracticeRoom> rooms = new ConcurrentHashMap<>();
  private final boolean enabled;
  private final int maxOpen;
  private final Duration lifetime;
  private final Clock clock;
  private final VideoRoomPasses videoPasses;
  private final IdentityApi identity;
  private final ObjectProvider<WhiteboardEditing> editing;

  PracticeRooms(
      @Value("${ayni.sessions.practice-rooms.enabled:false}") boolean enabled,
      @Value("${ayni.sessions.practice-rooms.max-open:20}") int maxOpen,
      @Value("${ayni.sessions.practice-rooms.lifetime:PT4H}") Duration lifetime,
      Clock clock,
      VideoRoomPasses videoPasses,
      IdentityApi identity,
      ObjectProvider<WhiteboardEditing> editing) {
    this.enabled = enabled;
    this.maxOpen = maxOpen;
    this.lifetime = lifetime;
    this.clock = clock;
    this.videoPasses = videoPasses;
    this.identity = identity;
    // Lazy: the board of a room is drawn through WhiteboardEditing, which asks this class first.
    this.editing = editing;
  }

  /**
   * Opens a new, empty room in the reader's university. Nobody is in it yet: the first person to
   * enter it is the tutor.
   *
   * @return the room's id, a random UUID, which is also the id of its session screen
   * @throws NoSuchElementException when practice rooms are not enabled
   * @throws PracticeRoomsFull when as many rooms are open as the backend keeps
   */
  public synchronized UUID open() {
    if (!enabled) {
      throw new NoSuchElementException("Practice rooms are not enabled");
    }
    String tenantId = TenantContext.require();
    forgetExpired();
    if (rooms.size() >= maxOpen) {
      throw new PracticeRoomsFull(rooms.size());
    }
    UUID id = UUID.randomUUID();
    String roomName = "ayni-" + UUID.randomUUID().toString().replace("-", "");
    rooms.put(id, PracticeRoom.open(id, tenantId, roomName, clock.instant(), lifetime));
    // Neither the id nor the room name: whoever holds them can enter.
    log.info("A practice room was opened; {} open", rooms.size());
    return id;
  }

  /** Whether {@code sessionId} is an open practice room of the reader's university. */
  public boolean isOpen(UUID sessionId) {
    if (!enabled || sessionId == null) {
      return false;
    }
    PracticeRoom room = rooms.get(sessionId);
    if (room == null) {
      return false;
    }
    if (room.isExpiredAt(clock.instant())) {
      forget(room);
      return false;
    }
    return room.getTenantId().equals(TenantContext.get());
  }

  /**
   * The room as the screen of a session reads it. Somebody who did not enter it yet reads it while
   * a seat is free, with the role they would take, so the screen can offer to enter.
   *
   * @throws NoSuchElementException when it is not an open room of this university
   * @throws NotAParticipant when both seats are taken by other people
   */
  public SessionDetails details(UUID sessionId, UUID userId) {
    Objects.requireNonNull(userId, "userId must not be null");
    return inRoom(sessionId, room -> detailsOf(room, userId, room.roleOf(userId)));
  }

  /**
   * Enters the room, as the tutor if it is empty and as the student otherwise, and hands over the
   * pass to its video call.
   *
   * @throws NoSuchElementException when it is not an open room of this university
   * @throws NotAParticipant when both seats are taken by other people
   */
  public JoinedSession join(UUID sessionId, UUID userId) {
    Objects.requireNonNull(userId, "userId must not be null");
    return inRoom(
        sessionId,
        room -> {
          Instant now = clock.instant();
          ParticipantRole role = room.enter(userId, now);
          return new JoinedSession(
              room.getId(),
              role,
              COURSE,
              counterpartOf(room, role),
              room.getRoomName(),
              room.status(),
              room.getStartedAt(),
              room.getOpenedAt(),
              room.getClosesAt(),
              room.joinedAtAs(ParticipantRole.STUDENT),
              room.joinedAtAs(ParticipantRole.TUTOR),
              videoPasses
                  .issue(room.getRoomName(), userId, role, now, room.getClosesAt())
                  .orElse(null));
        });
  }

  /** Any six digit code confirms presence: no code was sent. */
  public PresenceConfirmation confirmPresence(UUID sessionId, UUID userId) {
    Objects.requireNonNull(userId, "userId must not be null");
    return inRoom(
        sessionId,
        room ->
            new PresenceConfirmation(room.getId(), room.confirmPresence(userId, clock.instant())));
  }

  /** Answers as a resend would, and sends nothing. */
  public void resend(UUID sessionId, UUID userId) {
    Objects.requireNonNull(userId, "userId must not be null");
    inRoom(
        sessionId,
        room -> {
          room.requireResend(userId);
          return null;
        });
  }

  /**
   * Records that the person confirms the end. The second confirmation ends the room: its board
   * channels are closed and it is forgotten. Nothing is credited.
   */
  public EndedSession end(UUID sessionId, UUID userId) {
    Objects.requireNonNull(userId, "userId must not be null");
    PracticeRoom[] ended = new PracticeRoom[1];
    EndedSession answer =
        inRoom(
            sessionId,
            room -> {
              if (room.confirmEnd(userId, clock.instant())) {
                ended[0] = room;
              }
              return new EndedSession(
                  room.getId(),
                  room.status(),
                  room.endConfirmedAtOf(userId),
                  room.getEndedAt(),
                  room.getClosesAt());
            });
    if (ended[0] != null) {
      forget(ended[0]);
    }
    return answer;
  }

  /** Forgets every room whose lifetime is over, closing the channels of its board. */
  public void forgetExpired() {
    Instant now = clock.instant();
    rooms.values().stream().filter(room -> room.isExpiredAt(now)).toList().forEach(this::forget);
  }

  // The board, for WhiteboardEditing: the same rules as a stored board, kept in memory.

  WhiteboardView readBoard(UUID sessionId, UUID userId) {
    return inRoom(sessionId, room -> viewOf(room, room.boardFor(userId)));
  }

  void requireLive(UUID sessionId, UUID userId) {
    inRoom(
        sessionId,
        room -> {
          room.requireLive(userId);
          return null;
        });
  }

  AppliedOperation draw(UUID sessionId, UUID userId, BoardOperation operation) {
    return inRoom(
        sessionId,
        room ->
            new AppliedOperation(
                room.getId(), room.draw(userId, operation, clock.instant()), operation, userId));
  }

  WhiteboardView replace(
      UUID sessionId, UUID userId, List<BoardElement> elements, long seenVersion) {
    return inRoom(
        sessionId,
        room -> {
          room.replace(userId, elements, seenVersion, clock.instant());
          return viewOf(room, room.boardFor(userId));
        });
  }

  private <T> T inRoom(UUID sessionId, Function<PracticeRoom, T> work) {
    PracticeRoom room = sessionId == null ? null : rooms.get(sessionId);
    if (room == null || !room.getTenantId().equals(TenantContext.get())) {
      throw new NoSuchElementException("Session not found: " + sessionId);
    }
    synchronized (room) {
      if (room.isEnded() || room.isExpiredAt(clock.instant())) {
        throw new NoSuchElementException("Session not found: " + sessionId);
      }
      return work.apply(room);
    }
  }

  private void forget(PracticeRoom room) {
    if (rooms.remove(room.getId(), room)) {
      editing.getObject().closeLive(room.getId());
      log.info("A practice room was forgotten; {} open", rooms.size());
    }
  }

  private SessionDetails detailsOf(PracticeRoom room, UUID userId, ParticipantRole role) {
    Instant startedAt = room.getStartedAt();
    // Once both are in, whoever reads is one of them: anybody else was refused by roleOf. The code
    // counts as issued when the room started and lives as long as the room.
    PresenceState presence =
        startedAt == null
            ? null
            : new PresenceState(
                startedAt,
                room.getClosesAt(),
                room.presenceConfirmedAtOf(userId),
                PresenceCheck.MAX_ATTEMPTS);
    return new SessionDetails(
        room.getId(),
        null,
        room.personAs(ParticipantRole.STUDENT).orElse(null),
        room.personAs(ParticipantRole.TUTOR).orElse(null),
        null,
        COURSE,
        role,
        counterpartOf(room, role),
        room.status(),
        room.getOpenedAt(),
        room.getClosesAt(),
        room.getOpenedAt(),
        startedAt,
        room.joinedAtAs(ParticipantRole.STUDENT),
        room.joinedAtAs(ParticipantRole.TUTOR),
        List.of(),
        PracticeRoom.NAME,
        startedAt == null ? room.getOpenedAt() : startedAt,
        presence,
        room.endConfirmedAtOf(userId),
        room.getEndedAt(),
        room.getClosesAt(),
        null);
  }

  /** The other seat, named by identity; both {@code null} while nobody took it. */
  private SessionCounterpart counterpartOf(PracticeRoom room, ParticipantRole role) {
    ParticipantRole other =
        role == ParticipantRole.STUDENT ? ParticipantRole.TUTOR : ParticipantRole.STUDENT;
    return room.personAs(other)
        .map(id -> new SessionCounterpart(id, identity.namesOf(Set.of(id)).get(id)))
        .orElse(new SessionCounterpart(null, null));
  }

  private static WhiteboardView viewOf(PracticeRoom room, Whiteboard board) {
    BoardContent content = board.content();
    return new WhiteboardView(
        room.getId(),
        content.version(),
        content.elements(),
        board.hasActivity(),
        room.status() == SessionStatus.IN_PROGRESS,
        board.hasActivity() ? board.getUpdatedAt() : null);
  }
}
