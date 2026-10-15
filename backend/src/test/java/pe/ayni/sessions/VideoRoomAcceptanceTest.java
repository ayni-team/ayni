package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingConfirmed;

/**
 * Scenario 2 of US08 over HTTP against a real PostgreSQL: joining hands each participant a pass to
 * the video call of that session. One test per scenario of {@code
 * features/US08-join-session.feature} about the video call, with the same name. The pass is read
 * the way Jitsi reads it, checking its signature with the shared secret.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VideoRoomAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  private final UUID student = UUID.randomUUID();
  private final UUID tutor = UUID.randomUUID();
  private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private BookingApi booking;

  @Value("${ayni.sessions.video.jwt-secret}")
  private String secret;

  @BeforeEach
  void bookingIsConfirmed() {
    when(booking.isConfirmed(any())).thenReturn(true);
    when(booking.requireBooking(any()))
        .thenAnswer(
            call ->
                new BookingView(
                    call.getArgument(0),
                    student,
                    tutor,
                    UUID.randomUUID(),
                    now,
                    now.plus(Duration.ofHours(1)),
                    1,
                    "Recursión",
                    BookingStatus.CONFIRMED));
  }

  private UUID aSessionStartingAt(Instant start) {
    BookingConfirmed confirmed =
        new BookingConfirmed(
            UPC,
            UUID.randomUUID(),
            student,
            tutor,
            UUID.randomUUID(),
            start,
            start.plus(Duration.ofHours(1)),
            List.of(UUID.randomUUID()),
            Credits.of(1),
            now);
    transactions.executeWithoutResult(status -> events.publishEvent(confirmed));
    return jdbc.queryForObject(
        "select id from sessions.sessions where booking_id = ?", UUID.class, confirmed.bookingId());
  }

  private ResultActions join(UUID session, UUID who) throws Exception {
    return mockMvc.perform(
        post("/api/v1/sessions/{id}/join", session)
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", who.toString()));
  }

  private JsonNode passOf(ResultActions joined) throws Exception {
    String body = joined.andReturn().getResponse().getContentAsString();
    return VideoPassToken.verifiedClaims(JsonPath.read(body, "$.video.token"), secret);
  }

  private String roomOf(UUID session) {
    return jdbc.queryForObject(
        "select room_name from sessions.sessions where id = ?", String.class, session);
  }

  @Test
  @DisplayName("Joining hands a pass to the video call of that room only")
  void joiningHandsAPassToTheVideoCallOfThatRoomOnly() throws Exception {
    UUID session = aSessionStartingAt(now.plus(Duration.ofMinutes(5)));
    UUID another = aSessionStartingAt(now.plus(Duration.ofMinutes(6)));
    String room = roomOf(session);

    ResultActions ana =
        join(session, student)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.video.url").value("http://jitsi.test:8000/" + room))
            .andExpect(jsonPath("$.video.expiresAt").exists());
    ResultActions bruno = join(session, tutor).andExpect(status().isOk());

    JsonNode anaPass = passOf(ana);
    JsonNode brunoPass = passOf(bruno);
    for (JsonNode pass : List.of(anaPass, brunoPass)) {
      assertThat(pass.path("room").asText()).isEqualTo(room).isNotEqualTo(roomOf(another));
      assertThat(pass.path("iss").asText()).isEqualTo("ayni");
      assertThat(pass.path("aud").asText()).isEqualTo("jitsi");
      assertThat(pass.path("sub").asText()).isEqualTo("meet.jitsi");
    }
    assertThat(anaPass.at("/context/user/id").asText()).isEqualTo(student.toString());
    assertThat(anaPass.at("/context/user/moderator").asBoolean()).isFalse();
    assertThat(brunoPass.at("/context/user/id").asText()).isEqualTo(tutor.toString());
    assertThat(brunoPass.at("/context/user/moderator").asBoolean()).isTrue();

    // Signed with another secret, Jitsi would refuse it.
    String token =
        JsonPath.read(ana.andReturn().getResponse().getContentAsString(), "$.video.token");
    assertThatThrownBy(
            () -> VideoPassToken.verifiedClaims(token, "a-different-secret-of-thirty-two-chars"))
        .hasMessageContaining("signature");
  }

  @Test
  @DisplayName("The pass lasts a few minutes and never past the end of the session")
  void thePassLastsAFewMinutesAndNeverPastTheEndOfTheSession() throws Exception {
    UUID upcoming = aSessionStartingAt(now.plus(Duration.ofMinutes(5)));
    Instant endingSoonStart = now.minus(Duration.ofMinutes(58));
    UUID endingSoon = aSessionStartingAt(endingSoonStart);

    JsonNode early = passOf(join(upcoming, student).andExpect(status().isOk()));
    assertThat(early.path("exp").asLong() - early.path("iat").asLong())
        .isEqualTo(Duration.ofMinutes(5).toSeconds());
    assertThat(early.path("nbf").asLong()).isLessThanOrEqualTo(early.path("iat").asLong());
    assertThat(Instant.ofEpochSecond(early.path("iat").asLong()))
        .isBetween(now.minusSeconds(5), Instant.now().plusSeconds(1));

    JsonNode late = passOf(join(endingSoon, tutor).andExpect(status().isOk()));
    assertThat(late.path("exp").asLong())
        .isEqualTo(endingSoonStart.plus(Duration.ofHours(1)).getEpochSecond());
  }

  @Test
  @DisplayName("Somebody who is not a participant gets no pass")
  void somebodyWhoIsNotAParticipantGetsNoPass() throws Exception {
    UUID session = aSessionStartingAt(now.plus(Duration.ofMinutes(5)));

    join(session, UUID.randomUUID())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.video").doesNotExist())
        .andExpect(jsonPath("$.roomName").doesNotExist());
  }
}
