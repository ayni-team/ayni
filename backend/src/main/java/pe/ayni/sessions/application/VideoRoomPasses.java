package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pe.ayni.sessions.domain.model.ParticipantRole;

/**
 * Hands a participant who joins a session what lets them into its video call (US08).
 *
 * <p>A port: the adapter in place signs a token for the self hosted Jitsi of {@code docker
 * compose}. A pass opens one room only, the session's, and lasts a few minutes: it is asked for
 * every time somebody joins, a reconnection included, so nobody needs a long one.
 */
public interface VideoRoomPasses {

  /**
   * @param notAfter the pass never outlives this, the scheduled end of the session
   * @return the pass, or nothing when no video service is configured
   */
  Optional<VideoPass> issue(
      String roomName, UUID userId, ParticipantRole role, Instant now, Instant notAfter);

  /**
   * What the client opens the call with.
   *
   * @param url the room in the video service, without the token
   * @param token the signed token the service checks; it travels only in the answer to joining
   */
  record VideoPass(String url, String token, Instant expiresAt) {}
}
