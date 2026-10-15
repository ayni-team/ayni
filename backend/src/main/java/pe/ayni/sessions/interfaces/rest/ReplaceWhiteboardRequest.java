package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import pe.ayni.sessions.domain.model.BoardElement;

/** The whole board, and the version it replaces. */
public record ReplaceWhiteboardRequest(
    @Schema(description = "The version the client last read") @NotNull @PositiveOrZero Long version,
    @Schema(description = "Every element the board will hold") @NotNull List<BoardElement> elements) {}
