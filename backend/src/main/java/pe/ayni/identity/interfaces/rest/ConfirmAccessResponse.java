package pe.ayni.identity.interfaces.rest;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.identity.application.ConfirmAccessResult;

public record ConfirmAccessResponse(
        String sessionToken,
        String tenantId,
        UUID userId,
        Instant expiresAt) {

    static ConfirmAccessResponse from(
            ConfirmAccessResult result) {

        return new ConfirmAccessResponse(
                result.sessionToken(),
                result.tenantId(),
                result.userId(),
                result.expiresAt());
    }
}