package pe.ayni.sessions.application;

import java.time.Instant;

/**
 * The reader's own presence code, without the code: what the screen needs to ask for it, say it
 * expired, or say it is done.
 *
 * @param confirmedAt {@code null} until the code is typed in
 */
public record PresenceState(
    Instant issuedAt, Instant expiresAt, Instant confirmedAt, int attemptsLeft) {}
