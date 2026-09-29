package pe.ayni.identity.application;

/**
 * Builds the URL delivered to the user for a single-use access token.
 *
 * <p>The URL carries the token and nothing else. The university is read from the link itself when
 * it is confirmed, so the client never declares which university it belongs to, and the link of a
 * platform administrator, who belongs to none, has the same shape as everybody else's.
 */
public interface AccessLinkUrlBuilder {

    String build(String rawToken);
}
