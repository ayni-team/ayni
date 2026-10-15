package pe.ayni.sessions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Reads a video pass the way Jitsi does: checks its signature, then its claims (US08). */
final class VideoPassToken {

  private VideoPassToken() {}

  /** The claims of a token signed with the secret, or an error if it was not. */
  static JsonNode verifiedClaims(String token, String secret) throws Exception {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new AssertionError("A JWT has three parts");
    }
    ObjectMapper json = new ObjectMapper();
    JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
    if (!"HS256".equals(header.path("alg").asText())) {
      throw new AssertionError("Signed with " + header.path("alg").asText());
    }
    Mac hmac = Mac.getInstance("HmacSHA256");
    hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] expected =
        hmac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
    if (!MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(parts[2]))) {
      throw new AssertionError("The signature does not verify with the secret");
    }
    return json.readTree(Base64.getUrlDecoder().decode(parts[1]));
  }
}
