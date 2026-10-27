package pe.ayni.sessions.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.application.VideoRoomPasses.VideoPass;
import pe.ayni.sessions.domain.model.ParticipantRole;

/** The passes to Jitsi (US08), without Spring. */
class JitsiVideoRoomPassesTest {

  private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");
  private static final String SECRET = "a-secret-of-at-least-thirty-two-characters";

  private static JitsiVideoRoomPasses passes(String secret) {
    return passes(secret, false);
  }

  private static JitsiVideoRoomPasses passes(String secret, boolean signWithRealTime) {
    return new JitsiVideoRoomPasses(
        new ObjectMapper(), "https://video.ayni.pe", "ayni", "jitsi", "meet.jitsi", secret,
        Duration.ofMinutes(5), signWithRealTime);
  }

  private static JsonNode claimsOf(VideoPass pass) throws Exception {
    return new ObjectMapper()
        .readTree(Base64.getUrlDecoder().decode(pass.token().split("\\.")[1]));
  }

  @Test
  @DisplayName("a pass names its room, lasts five minutes and goes to the room's address")
  void aPassNamesItsRoom() throws Exception {
    VideoPass pass =
        passes(SECRET)
            .issue("ayni-room", UUID.randomUUID(), ParticipantRole.STUDENT, NOW,
                NOW.plus(Duration.ofHours(1)))
            .orElseThrow();

    assertThat(pass.url()).isEqualTo("https://video.ayni.pe/ayni-room");
    assertThat(pass.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    JsonNode claims = claimsOf(pass);
    assertThat(claims.path("room").asText()).isEqualTo("ayni-room");
    assertThat(claims.path("exp").asLong()).isEqualTo(pass.expiresAt().getEpochSecond());
    assertThat(claims.path("nbf").asLong()).isEqualTo(NOW.minusSeconds(30).getEpochSecond());
  }

  @Test
  @DisplayName("a pass never outlives the session")
  void neverOutlivesTheSession() {
    VideoPass pass =
        passes(SECRET)
            .issue("ayni-room", UUID.randomUUID(), ParticipantRole.TUTOR, NOW,
                NOW.plus(Duration.ofMinutes(2)))
            .orElseThrow();

    assertThat(pass.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
  }

  @Test
  @DisplayName("without a secret there are no passes, and a short secret is refused")
  void withoutASecretThereAreNoPasses() {
    assertThat(
            passes("")
                .issue("ayni-room", UUID.randomUUID(), ParticipantRole.STUDENT, NOW,
                    NOW.plus(Duration.ofHours(1))))
        .isEmpty();
    assertThatThrownBy(() -> passes("too-short")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("A video pass is signed with the real time while the clock is moved")
  void aVideoPassIsSignedWithTheRealTimeWhileTheClockIsMoved() throws Exception {
    Instant before = Instant.now();
    Instant movedNow = before.plus(Duration.ofDays(3));

    VideoPass whole =
        passes(SECRET, true)
            .issue("ayni-room", UUID.randomUUID(), ParticipantRole.STUDENT, movedNow,
                movedNow.plus(Duration.ofHours(1)))
            .orElseThrow();
    VideoPass shortened =
        passes(SECRET, true)
            .issue("ayni-room", UUID.randomUUID(), ParticipantRole.TUTOR, movedNow,
                movedNow.plus(Duration.ofMinutes(2)))
            .orElseThrow();
    Instant after = Instant.now();

    JsonNode claims = claimsOf(whole);
    assertThat(claims.path("iat").asLong())
        .isBetween(before.getEpochSecond(), after.getEpochSecond());
    assertThat(claims.path("nbf").asLong()).isEqualTo(claims.path("iat").asLong() - 30);
    assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(300);
    // The answer stays on the application's clock, as every other instant of joining.
    assertThat(whole.expiresAt()).isEqualTo(movedNow.plus(Duration.ofMinutes(5)));

    JsonNode shortClaims = claimsOf(shortened);
    assertThat(shortClaims.path("exp").asLong() - shortClaims.path("iat").asLong())
        .isEqualTo(120);
    assertThat(shortened.expiresAt()).isEqualTo(movedNow.plus(Duration.ofMinutes(2)));
  }

  @Test
  @DisplayName("outside the demo profile a pass is signed with the instant it is given")
  void outsideTheDemoProfileAPassIsSignedWithTheInstantItIsGiven() throws Exception {
    Instant movedNow = Instant.now().plus(Duration.ofDays(3));

    JsonNode claims =
        claimsOf(
            passes(SECRET)
                .issue("ayni-room", UUID.randomUUID(), ParticipantRole.STUDENT, movedNow,
                    movedNow.plus(Duration.ofHours(1)))
                .orElseThrow());

    assertThat(claims.path("iat").asLong()).isEqualTo(movedNow.getEpochSecond());
    assertThat(claims.path("exp").asLong())
        .isEqualTo(movedNow.plus(Duration.ofMinutes(5)).getEpochSecond());
  }
}
