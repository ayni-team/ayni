package pe.ayni.audit.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Why a coordinator dismisses an alert. */
@Schema(name = "DismissAnomalyRequest")
public record DismissAnomalyRequest(
    @Schema(
            description = "Required, at most 500 characters",
            example = "They are study partners and the sessions are planned")
        String reason) {}
