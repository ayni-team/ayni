package pe.ayni.sessions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.domain.model.SessionRuleViolation;

/** A session is born scheduled, in a room of its own. */
class SessionTest {

  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
  private static final Instant NINE = NOW.plus(Duration.ofDays(1));

  private static Session scheduled(Instant start, Instant end) {
    return Session.schedule(
        UUID.randomUUID(), "UPC", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start,
        end, NOW);
  }

  @Test
  @DisplayName("a session is born scheduled for the booked hours")
  void startsScheduled() {

    Session session = scheduled(NINE, NINE.plus(Duration.ofHours(2)));

    assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    assertThat(session.getScheduledStart()).isEqualTo(NINE);
    assertThat(session.getScheduledEnd()).isEqualTo(NINE.plus(Duration.ofHours(2)));
    assertThat(session.getStartedAt()).isNull();
    assertThat(session.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("every session gets a room name of 128 random bits, never the same twice")
  void getsAnUnguessableRoom() {

    Session one = scheduled(NINE, NINE.plus(Duration.ofHours(1)));
    Session another = scheduled(NINE, NINE.plus(Duration.ofHours(1)));

    assertThat(one.getRoomName()).matches("ayni-[0-9a-f]{32}");
    assertThat(one.getRoomName()).isNotEqualTo(another.getRoomName());
  }

  @Test
  @DisplayName("a session that ends before it starts is refused")
  void refusesABackwardsSession() {
    assertThatThrownBy(() -> scheduled(NINE, NINE)).isInstanceOf(SessionRuleViolation.class);
  }
}
