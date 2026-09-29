package pe.ayni.sessions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.PresenceCheckUnavailable;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.domain.model.SessionNotOpen;
import pe.ayni.sessions.domain.model.SessionRuleViolation;

/** A session is born scheduled, in a room of its own, and only its two participants get in. */
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

  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();

  private static Session aSessionAtNine() {
    return Session.schedule(
        UUID.randomUUID(), "UPC", UUID.randomUUID(), STUDENT, TUTOR, NINE,
        NINE.plus(Duration.ofHours(1)), NOW);
  }

  @Test
  @DisplayName("the student and the tutor are its participants, and nobody else is")
  void knowsItsParticipants() {

    Session session = aSessionAtNine();

    assertThat(session.roleOf(STUDENT)).isEqualTo(ParticipantRole.STUDENT);
    assertThat(session.roleOf(TUTOR)).isEqualTo(ParticipantRole.TUTOR);
    assertThatThrownBy(() -> session.roleOf(UUID.randomUUID()))
        .isInstanceOf(NotAParticipant.class);
  }

  @Test
  @DisplayName("the first participant to join starts it, and joining again changes nothing")
  void theFirstJoinStartsIt() {

    Session session = aSessionAtNine();
    Instant tenToNine = NINE.minus(Duration.ofMinutes(10));

    assertThat(session.join(STUDENT, tenToNine)).isTrue();
    assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    assertThat(session.getStartedAt()).isEqualTo(tenToNine);

    assertThat(session.join(TUTOR, NINE)).isFalse();
    assertThat(session.join(STUDENT, NINE.plus(Duration.ofMinutes(20)))).isFalse();
    assertThat(session.getStartedAt()).isEqualTo(tenToNine);
  }

  @Test
  @DisplayName("the room opens fifteen minutes before the start, not a second earlier")
  void theRoomOpensFifteenMinutesBefore() {

    Session session = aSessionAtNine();

    assertThat(session.joinOpensAt()).isEqualTo(NINE.minus(Duration.ofMinutes(15)));
    assertThatThrownBy(() -> session.join(STUDENT, NINE.minus(Duration.ofMinutes(15)).minusSeconds(1)))
        .isInstanceOf(SessionNotOpen.class)
        .hasMessageContaining("opens fifteen minutes before");
    assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);

    assertThat(session.join(STUDENT, NINE.minus(Duration.ofMinutes(15)))).isTrue();
  }

  @Test
  @DisplayName("the room closes at the scheduled end")
  void theRoomClosesAtTheEnd() {

    Session session = aSessionAtNine();

    assertThatThrownBy(() -> session.join(TUTOR, NINE.plus(Duration.ofHours(1))))
        .isInstanceOf(SessionNotOpen.class)
        .hasMessage("This session has already ended");
  }

  @Test
  @DisplayName("somebody else cannot join, whenever they try")
  void somebodyElseCannotJoin() {

    Session session = aSessionAtNine();

    assertThatThrownBy(() -> session.join(UUID.randomUUID(), NINE))
        .isInstanceOf(NotAParticipant.class);
    assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
  }

  @Test
  @DisplayName("presence codes are due five minutes after the scheduled start, however early it began")
  void presenceCodesAreDueFiveMinutesIn() {

    Session session = aSessionAtNine();
    session.join(STUDENT, NINE.minus(Duration.ofMinutes(15)));

    assertThat(session.presenceCheckAt()).isEqualTo(NINE.plus(Duration.ofMinutes(5)));
    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofMinutes(4)))).isFalse();
    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofMinutes(5)))).isTrue();
    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofMinutes(59)))).isTrue();
    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofHours(1)))).isFalse();
  }

  @Test
  @DisplayName("a session nobody joined has nobody to check, and presence waits for it to start")
  void aSessionNotStartedIsNotChecked() {

    Session session = aSessionAtNine();

    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofMinutes(10)))).isFalse();
    assertThatThrownBy(session::requireInProgressForPresence)
        .isInstanceOf(PresenceCheckUnavailable.class)
        .hasMessageContaining("scheduled");

    session.join(TUTOR, NINE.plus(Duration.ofMinutes(20)));

    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofMinutes(20)))).isTrue();
    session.requireInProgressForPresence();
  }

  @Test
  @DisplayName("a session closes completed or unverified, once, and only while in progress")
  void closesOnce() {

    Session verified = aSessionAtNine();
    assertThatThrownBy(() -> verified.close(true, NINE))
        .as("a session nobody joined cannot be closed")
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(verified::requireInProgressToEnd).isInstanceOf(SessionNotOpen.class);

    verified.join(STUDENT, NINE);
    verified.requireInProgressToEnd();
    verified.close(true, NINE.plus(Duration.ofMinutes(58)));
    assertThat(verified.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    assertThat(verified.getEndedAt()).isEqualTo(NINE.plus(Duration.ofMinutes(58)));
    assertThatThrownBy(() -> verified.close(false, NINE.plus(Duration.ofMinutes(59))))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(verified::requireInProgressToEnd)
        .isInstanceOf(SessionNotOpen.class)
        .hasMessageContaining("completed");

    Session unverified = aSessionAtNine();
    unverified.join(TUTOR, NINE);
    unverified.close(false, NINE.plus(Duration.ofMinutes(58)));
    assertThat(unverified.getStatus()).isEqualTo(SessionStatus.UNVERIFIED);
  }

  @Test
  @DisplayName("a session left open closes on its own fifteen minutes after the booked hour")
  void closesOnItsOwnFifteenMinutesAfter() {

    Session session = aSessionAtNine();
    Instant quarterPast = NINE.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(15));

    assertThat(session.closesAt()).isEqualTo(quarterPast);
    assertThat(session.isDueToClose(quarterPast)).as("nobody joined: nothing to close").isFalse();

    session.join(STUDENT, NINE);
    assertThat(session.isDueToClose(quarterPast.minusSeconds(1))).isFalse();
    assertThat(session.isDueToClose(quarterPast)).isTrue();
  }

  @Test
  @DisplayName("the tutor earns the hours that were booked")
  void earnsTheBookedHours() {

    Session twoHours =
        Session.schedule(
            UUID.randomUUID(), "UPC", UUID.randomUUID(), STUDENT, TUTOR, NINE,
            NINE.plus(Duration.ofHours(2)), NOW);

    assertThat(twoHours.bookedHours()).isEqualTo(2);
    assertThat(aSessionAtNine().bookedHours()).isEqualTo(1);
  }
}
