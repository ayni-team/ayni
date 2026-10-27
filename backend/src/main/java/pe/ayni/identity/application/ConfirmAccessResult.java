package pe.ayni.identity.application;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.identity.domain.model.AccessRole;

/**
 * The session a confirmed link opened.
 *
 * @param sessionToken the session token in clear. Only its hash is stored, so this is the one time
 *     it exists outside the client
 * @param tenantId the university of the session, {@code null} for a platform administrator
 * @param userId the student or coordinator, or the platform administrator
 * @param role what the session may call (TS03)
 */
public record ConfirmAccessResult(
        String sessionToken,
        String tenantId,
        UUID userId,
        AccessRole role,
        Instant expiresAt) {}
