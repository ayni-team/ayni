package pe.ayni.identity.application;

/**
 * Generates and hashes secure tokens used by the Identity access flow.
 */
public interface AccessTokenGenerator {

    GeneratedAccessToken generate();

    String hash(String rawToken);
}