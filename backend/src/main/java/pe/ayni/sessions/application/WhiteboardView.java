package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.sessions.domain.model.BoardElement;

/**
 * A whiteboard as a participant reads it: every element and the version they correspond to.
 *
 * @param live whether it can still be drawn on, which is while the session is in progress
 * @param updatedAt when it last changed, {@code null} when nothing was ever stored
 */
public record WhiteboardView(
    UUID sessionId,
    long version,
    List<BoardElement> elements,
    boolean hasActivity,
    boolean live,
    Instant updatedAt) {}
