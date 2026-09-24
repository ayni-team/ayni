package pe.ayni.identity.application;

import java.time.Instant;
import java.util.UUID;

public record ConfirmAccessResult(
        String sessionToken,
        String tenantId,
        UUID userId,
        Instant expiresAt) {}