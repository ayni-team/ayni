package pe.ayni.identity.application;

import java.time.Instant;
import java.util.UUID;

/**
 * The session a confirmed link opened.
 *
 * @param sessionToken the session token in clear. Only its hash is stored, so this is the one time
 *     it exists outside the client
 */
public record ConfirmAccessResult(
        String sessionToken,
        String tenantId,
        UUID userId,
        Instant expiresAt) {}
