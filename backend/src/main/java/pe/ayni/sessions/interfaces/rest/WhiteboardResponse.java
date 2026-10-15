package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.sessions.application.WhiteboardView;
import pe.ayni.sessions.domain.model.BoardElement;

/** A whiteboard as a participant reads it. */
@Schema(name = "Whiteboard")
public record WhiteboardResponse(
    UUID sessionId,
    @Schema(description = "How many operations it has applied; send it back to replace the board")
        long version,
    @Schema(description = "What is drawn, in the order it was first drawn") List<BoardElement> elements,
    @Schema(description = "Whether anything was ever drawn on it") boolean hasActivity,
    @Schema(description = "Whether it can still be drawn on: while the session is in progress")
        boolean live,
    @Schema(description = "When it last changed, UTC; absent when nothing was ever stored")
        Instant updatedAt) {

  static WhiteboardResponse of(WhiteboardView board) {
    return new WhiteboardResponse(
        board.sessionId(),
        board.version(),
        board.elements(),
        board.hasActivity(),
        board.live(),
        board.updatedAt());
  }
}
