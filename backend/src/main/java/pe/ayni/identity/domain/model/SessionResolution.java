package pe.ayni.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * What a session token turned out to be.
 *
 * <p>Only {@link Outcome#OPEN} carries somebody: a token that is unknown, expired or revoked says
 * nothing about who presented it, and the request it came with is refused before anything is read
 * on its behalf.
 *
 * @param tenantId the university of the session, {@code null} for a platform administrator
 * @param subjectId the student or coordinator, or the platform administrator
 */
public record SessionResolution(Outcome outcome, String tenantId, UUID subjectId, AccessRole role) {

    public enum Outcome {
        OPEN,
        UNKNOWN,
        EXPIRED,
        REVOKED
    }

    public static SessionResolution open(String tenantId, UUID subjectId, AccessRole role) {
        return new SessionResolution(
                Outcome.OPEN,
                tenantId,
                Objects.requireNonNull(subjectId, "subjectId must not be null"),
                Objects.requireNonNull(role, "role must not be null"));
    }

    public static SessionResolution refused(Outcome outcome) {
        if (outcome == Outcome.OPEN) {
            throw new IllegalArgumentException("An open session names somebody");
        }
        return new SessionResolution(outcome, null, null, null);
    }

    public boolean isOpen() {
        return outcome == Outcome.OPEN;
    }
}
