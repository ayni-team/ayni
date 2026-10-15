package pe.ayni.sessions.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.ayni.sessions.application.VideoRoomPasses;
import pe.ayni.sessions.domain.model.ParticipantRole;

/**
 * Passes to the self hosted Jitsi of {@code docker compose}, which accepts only the tokens it can
 * verify (US08, {@code AUTH_TYPE=jwt}).
 *
 * <p>Each one is a JSON Web Token signed with HS256 and the secret both share. It names the room of
 * the session ({@code room}), so it opens no other; it is for Ayni's Jitsi ({@code iss}, {@code
 * aud}, {@code sub}); and it lasts {@code ayni.sessions.video.token-ttl}, five minutes by default,
 * and never past the scheduled end of the session. Jitsi checks the expiry when somebody enters, so
 * a short pass is enough: joining again issues a new one. The tutor enters as moderator.
 *
 * <p>The secret comes from {@code AYNI_JITSI_JWT_SECRET} and has no default here: {@code
 * docker-compose.yml} carries the one of development. Without it the application issues no passes,
 * says so once at startup, and joining still hands over the room.
 */
@Component
class JitsiVideoRoomPasses implements VideoRoomPasses {

  private static final Logger log = LoggerFactory.getLogger(JitsiVideoRoomPasses.class);

  private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

  /** Tolerated difference between this clock and Jitsi's when it checks {@code nbf}. */
  private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

  private final ObjectMapper json;
  private final String baseUrl;
  private final String appId;
  private final String audience;
  private final String domain;
  private final byte[] secret;
  private final Duration ttl;

  JitsiVideoRoomPasses(
      ObjectMapper json,
      @Value("${ayni.sessions.video.url:http://localhost:8000}") String baseUrl,
      @Value("${ayni.sessions.video.app-id:ayni}") String appId,
      @Value("${ayni.sessions.video.audience:jitsi}") String audience,
      @Value("${ayni.sessions.video.domain:meet.jitsi}") String domain,
      @Value("${ayni.sessions.video.jwt-secret:}") String secret,
      @Value("${ayni.sessions.video.token-ttl:PT5M}") Duration ttl) {
    this.json = json;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.appId = appId;
    this.audience = audience;
    this.domain = domain;
    this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    this.ttl = ttl;
    if (this.secret.length == 0) {
      log.warn("No AYNI_JITSI_JWT_SECRET is set: joining a session hands over no video pass");
    } else if (this.secret.length < 32) {
      throw new IllegalStateException("AYNI_JITSI_JWT_SECRET must have at least 32 characters");
    }
  }

  @Override
  public Optional<VideoPass> issue(
      String roomName, UUID userId, ParticipantRole role, Instant now, Instant notAfter) {
    if (secret.length == 0) {
      return Optional.empty();
    }
    Instant expiresAt = now.plus(ttl).isBefore(notAfter) ? now.plus(ttl) : notAfter;

    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", userId.toString());
    user.put("name", role == ParticipantRole.TUTOR ? "Tutor" : "Estudiante");
    user.put("moderator", role == ParticipantRole.TUTOR);

    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("iss", appId);
    claims.put("aud", audience);
    claims.put("sub", domain);
    claims.put("room", roomName);
    claims.put("iat", now.getEpochSecond());
    claims.put("nbf", now.minus(CLOCK_SKEW).getEpochSecond());
    claims.put("exp", expiresAt.getEpochSecond());
    claims.put("context", Map.of("user", user));

    return Optional.of(new VideoPass(baseUrl + roomName, sign(claims), expiresAt));
  }

  private String sign(Map<String, Object> claims) {
    try {
      String header = encode(Map.of("alg", "HS256", "typ", "JWT"));
      String payload = encode(claims);
      Mac hmac = Mac.getInstance("HmacSHA256");
      hmac.init(new SecretKeySpec(secret, "HmacSHA256"));
      byte[] signature =
          hmac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
      return header + "." + payload + "." + BASE64URL.encodeToString(signature);
    } catch (NoSuchAlgorithmException | InvalidKeyException | JsonProcessingException failure) {
      throw new IllegalStateException("A video pass could not be signed", failure);
    }
  }

  private String encode(Map<String, Object> part) throws JsonProcessingException {
    return BASE64URL.encodeToString(json.writeValueAsBytes(part));
  }
}
