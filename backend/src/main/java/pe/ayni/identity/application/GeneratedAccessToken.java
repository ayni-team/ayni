package pe.ayni.identity.application;

/**
 * A newly generated access token.
 *
 * @param rawToken value sent to the user
 * @param tokenHash value persisted by Identity
 */
public record GeneratedAccessToken(
        String rawToken,
        String tokenHash) {}