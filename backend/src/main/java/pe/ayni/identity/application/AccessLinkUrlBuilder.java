package pe.ayni.identity.application;

/**
 * Builds the URL delivered to the user for a single-use access token.
 */
public interface AccessLinkUrlBuilder {

    String build(String rawToken);
}