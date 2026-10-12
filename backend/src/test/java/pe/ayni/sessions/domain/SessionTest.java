package pe.ayni.sessions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.Participation;
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

  /** The first arrival of a participant, as joining records it. */
  private static Participation arrival(Session session, UUID user, Instant at) {
    return Participation.arrived(
        UUID.randomUUID(), "UPC", session.getId(), user, session.roleOf(user), at);
  }

  /** A session both participants checked in to, started when the second arrived. */
  private static Session aSessionStartedAt(Instant studentArrived, Instant tutorArrived) {
    Session session = aSessionAtNine();
    session.join(STUDENT, studentArrived);
    session.join(TUTOR, tutorArrived);
    Instant startedAt = studentArrived.isAfter(tutorArrived) ? studentArrived : tutorArrived;
    assertThat(
            session.startIfBothCheckedIn(
                List.of(
                    arrival(session, STUDENT, studentArrived),
                    arrival(session, TUTOR, tutorArrived)),
                startedAt))
        .isTrue();
    return session;
  }

  @Test
  @DisplayName("joining does not start it: it starts when the second participant has checked in")
  void startsWhenTheSecondParticipantChecksIn() {

    Session session = aSessionAtNine();
    Instant tenToNine = NINE.minus(Duration.ofMinutes(10));
    Instant fiveToNine = NINE.minus(Duration.ofMinutes(5));

    session.join(STUDENT, tenToNine);
    List<Participation> onlyTheStudent = List.of(arrival(session, STUDENT, tenToNine));
    assertThat(session.startIfBothCheckedIn(onlyTheStudent, tenToNine)).isFalse();
    assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    assertThat(session.getStartedAt()).isNull();

    session.join(TUTOR, fiveToNine);
    List<Participation> both =
        List.of(arrival(session, STUDENT, tenToNine), arrival(session, TUTOR, fiveToNine));
    assertThat(session.startIfBothCheckedIn(both, fiveToNine)).isTrue();
    assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    assertThat(session.getStartedAt()).as("when the second arrived").isEqualTo(fiveToNine);

    // Coming back changes nothing, and it does not start twice.
    session.join(STUDENT, NINE.plus(Duration.ofMinutes(20)));
    assertThat(session.startIfBothCheckedIn(both, NINE.plus(Duration.ofMinutes(20)))).isFalse();
    assertThat(session.getStartedAt()).isEqualTo(fiveToNine);
  }

  @Test
  @DisplayName("a participant who arrives at the ten minute mark or later has not checked in")
  void theDeadlineIsStrict() {

    Session session = aSessionAtNine();
    Instant deadline = NINE.plus(Duration.ofMinutes(10));
    Participation student = arrival(session, STUDENT, NINE);
    Participation justInTime = arrival(session, TUTOR, deadline.minusSeconds(1));
    Participation atTheMark = arrival(session, TUTOR, deadline);

    assertThat(session.checkedInOnTime(TUTOR, List.of(justInTime))).isTrue();
    assertThat(session.checkedInOnTime(TUTOR, List.of(atTheMark))).isFalse();
    assertThat(session.startIfBothCheckedIn(List.of(student, atTheMark), deadline)).isFalse();
    assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    assertThat(session.absentAtDeadline(List.of(student, atTheMark))).containsExactly(TUTOR);
    assertThat(session.absentAtDeadline(List.of(student, justInTime))).isEmpty();
    assertThat(session.absentAtDeadline(List.of())).containsExactly(STUDENT, TUTOR);
    assertThat(session.startIfBothCheckedIn(List.of(student, justInTime), deadline.minusSeconds(1)))
        .isTrue();
  }

  @Test
  @DisplayName("a session already abandoned is not started by a late arrival")
  void anAbandonedSessionDoesNotStart() {

    Session session = aSessionAtNine();
    session.abandon(NINE.plus(Duration.ofMinutes(10)));

    assertThat(
            session.startIfBothCheckedIn(
                List.of(arrival(session, STUDENT, NINE), arrival(session, TUTOR, NINE)),
                NINE.plus(Duration.ofMinutes(11))))
        .isFalse();
    assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
    assertThat(session.getStartedAt()).isNull();
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

    session.join(STUDENT, NINE.minus(Duration.ofMinutes(15)));
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

    Session session =
        aSessionStartedAt(NINE.minus(Duration.ofMinutes(15)), NINE.minus(Duration.ofMinutes(14)));

    assertThat(session.presenceCheckAt()).isEqualTo(NINE.plus(Duration.ofMinutes(5)));
    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofMinutes(4)))).isFalse();
    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofMinutes(5)))).isTrue();
    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofMinutes(59)))).isTrue();
    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofHours(1)))).isFalse();
  }

  @Test
  @DisplayName("a session that never started has nobody to check, however many minutes pass")
  void aSessionNotStartedIsNotChecked() {

    Session session = aSessionAtNine();
    session.join(STUDENT, NINE.minus(Duration.ofMinutes(5)));

    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofMinutes(5)))).isFalse();
    assertThat(session.isDueForPresenceCheck(NINE.plus(Duration.ofMinutes(30)))).isFalse();
    assertThatThrownBy(session::requireInProgressForPresence)
        .isInstanceOf(PresenceCheckUnavailable.class)
        .hasMessageContaining("scheduled");
    assertThat(session.isDueToClose(NINE.plus(Duration.ofHours(2)))).isFalse();
    assertThatThrownBy(() -> session.close(false, NINE.plus(Duration.ofHours(2))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("a session closes completed or unverified, once, and only while in progress")
  void closesOnce() {

    Session neverStarted = aSessionAtNine();
    assertThatThrownBy(() -> neverStarted.close(true, NINE))
        .as("a session that never started cannot be closed")
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(neverStarted::requireInProgressToEnd).isInstanceOf(SessionNotOpen.class);

    Session verified = aSessionStartedAt(NINE, NINE);
    verified.requireInProgressToEnd();
    verified.close(true, NINE.plus(Duration.ofMinutes(58)));
    assertThat(verified.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    assertThat(verified.getEndedAt()).isEqualTo(NINE.plus(Duration.ofMinutes(58)));
    assertThatThrownBy(() -> verified.close(false, NINE.plus(Duration.ofMinutes(59))))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(verified::requireInProgressToEnd)
        .isInstanceOf(SessionNotOpen.class)
        .hasMessageContaining("completed");

    Session unverified = aSessionStartedAt(NINE, NINE);
    unverified.close(false, NINE.plus(Duration.ofMinutes(58)));
    assertThat(unverified.getStatus()).isEqualTo(SessionStatus.UNVERIFIED);
  }

  @Test
  @DisplayName("a session left open closes on its own fifteen minutes after the booked hour")
  void closesOnItsOwnFifteenMinutesAfter() {

    Session session = aSessionAtNine();
    Instant quarterPast = NINE.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(15));

    assertThat(session.closesAt()).isEqualTo(quarterPast);
    assertThat(session.isDueToClose(quarterPast)).as("never started: nothing to close").isFalse();

    Session started = aSessionStartedAt(NINE, NINE);
    assertThat(started.isDueToClose(quarterPast.minusSeconds(1))).isFalse();
    assertThat(started.isDueToClose(quarterPast)).isTrue();
  }

  @Test
  @DisplayName("a session is abandoned ten minutes after the start if somebody did not check in")
  void abandonsAfterTutorNoShowDeadline() {

    Session scheduled = aSessionAtNine();
    Instant deadline = NINE.plus(Duration.ofMinutes(10));

    assertThat(scheduled.checkInDeadline()).isEqualTo(deadline);
    assertThat(scheduled.isDueForTutorNoShow(deadline.minusSeconds(1))).isFalse();
    assertThat(scheduled.isDueForTutorNoShow(deadline)).isTrue();

    scheduled.abandon(deadline);

    assertThat(scheduled.getStatus()).isEqualTo(SessionStatus.ABANDONED);
    assertThat(scheduled.getEndedAt()).isEqualTo(deadline);
    assertThat(scheduled.isDueForTutorNoShow(deadline.plusSeconds(1))).isFalse();
  }

  @Test
  @DisplayName("a missing participant is detected even when the other one joined")
  void abandonsWhenOnlyOneParticipantArrived() {

    Session session = aSessionAtNine();
    session.join(STUDENT, NINE);

    assertThat(session.isDueForTutorNoShow(NINE.plus(Duration.ofMinutes(10)))).isTrue();
    session.abandon(NINE.plus(Duration.ofMinutes(10)));
    assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
  }

  @Test
  @DisplayName("a session cannot be abandoned after it has already ended")
  void cannotAbandonClosedSession() {

    Session session = aSessionStartedAt(NINE, NINE);
    session.close(false, NINE.plus(Duration.ofHours(1)));

    assertThat(session.isDueForTutorNoShow(NINE.plus(Duration.ofMinutes(10)))).isFalse();
    assertThatThrownBy(() -> session.abandon(NINE.plus(Duration.ofMinutes(10))))
        .isInstanceOf(IllegalStateException.class);
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
