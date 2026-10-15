package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;

/**
 * A session of a participant's history, as they review it afterwards (US12).
 *
 * @param statusNote what happened to it, in a sentence, so a session that did not take place
 *     shows why instead of an empty workspace
 * @param realDurationMinutes from both being in to the close, {@code null} when it never took place
 * @param whiteboard the board as it was left, {@code null} when the session never started
 * @param supportMaterial what was prepared from the need, {@code null} when there was none
 * @param absentParticipantIds who did not come, for a session abandoned for that reason
 */
public record SessionReview(
    UUID sessionId,
    SessionStatus status,
    String statusNote,
    Instant scheduledStart,
    Instant scheduledEnd,
    Instant startedAt,
    Instant endedAt,
    long bookedMinutes,
    Long realDurationMinutes,
    String needDescription,
    SupportMaterialView supportMaterial,
    WhiteboardView whiteboard,
    List<UUID> absentParticipantIds) {}
