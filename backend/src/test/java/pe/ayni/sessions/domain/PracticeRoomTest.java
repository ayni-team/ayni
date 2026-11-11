package pe.ayni.sessions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.BoardElement;
import pe.ayni.sessions.domain.model.BoardOperation;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.PracticeRoom;
import pe.ayni.sessions.domain.model.PresenceCheckUnavailable;
import pe.ayni.sessions.domain.model.PresenceResendRefused;
import pe.ayni.sessions.domain.model.SessionNotOpen;
import pe.ayni.sessions.domain.model.WhiteboardRuleViolation;
import pe.ayni.sessions.domain.model.WhiteboardVersionConflict;

/** The rules of a practice room, without Spring. */
class PracticeRoomTest {

  private static final Instant OPENED = Instant.parse("2026-11-10T15:00:00Z");

  private final UUID ana = UUID.randomUUID();
  private final UUID bruno = UUID.randomUUID();
  private final UUID carla = UUID.randomUUID();

  private PracticeRoom aRoom() {
    return PracticeRoom.open(
        UUID.randomUUID(), "UPC", "ayni-room", OPENED, Duration.ofHours(4));
  }

  private PracticeRoom bothIn() {
    PracticeRoom room = aRoom();
    room.enter(ana, OPENED.plusSeconds(10));
    room.enter(bruno, OPENED.plusSeconds(20));
    return room;
  }

  private static BoardOperation put(String id) {
    return new BoardOperation(
        "op-" + id, BoardOperation.Action.PUT_ELEMENT, new BoardElement(id, "stroke", Map.of()), null);
  }

  @Test
  void theFirstIsTheTutorTheSecondTheStudentAndNobodyElseGetsIn() {
    PracticeRoom room = aRoom();
    assertThat(room.roleOf(carla)).isEqualTo(ParticipantRole.TUTOR);
    assertThat(room.status()).isEqualTo(SessionStatus.SCHEDULED);

    assertThat(room.enter(ana, OPENED.plusSeconds(10))).isEqualTo(ParticipantRole.TUTOR);
    assertThat(room.roleOf(carla)).isEqualTo(ParticipantRole.STUDENT);
    assertThat(room.enter(bruno, OPENED.plusSeconds(20))).isEqualTo(ParticipantRole.STUDENT);

    assertThat(room.status()).isEqualTo(SessionStatus.IN_PROGRESS);
    assertThat(room.getStartedAt()).isEqualTo(OPENED.plusSeconds(20));
    assertThat(room.enter(ana, OPENED.plusSeconds(30))).isEqualTo(ParticipantRole.TUTOR);
    assertThat(room.joinedAtAs(ParticipantRole.TUTOR)).isEqualTo(OPENED.plusSeconds(10));
    assertThatThrownBy(() -> room.enter(carla, OPENED.plusSeconds(40)))
        .isInstanceOf(NotAParticipant.class);
    assertThatThrownBy(() -> room.roleOf(carla)).isInstanceOf(NotAParticipant.class);
  }

  @Test
  void onlyWhoEnteredReadsTheBoardAndOnlyWhileBothAreInItIsDrawnOn() {
    PracticeRoom room = aRoom();
    room.enter(ana, OPENED);
    assertThatThrownBy(() -> room.boardFor(carla)).isInstanceOf(NotAParticipant.class);
    assertThat(room.boardFor(ana).content().version()).isZero();
    assertThatThrownBy(() -> room.draw(ana, put("a"), OPENED)).isInstanceOf(SessionNotOpen.class);

    room.enter(bruno, OPENED.plusSeconds(5));
    assertThat(room.draw(ana, put("a"), OPENED.plusSeconds(6))).isEqualTo(1);
    assertThat(room.draw(bruno, put("b"), OPENED.plusSeconds(7))).isEqualTo(2);
    assertThatThrownBy(() -> room.replace(ana, List.of(), 1, OPENED.plusSeconds(8)))
        .isInstanceOf(WhiteboardVersionConflict.class);
    room.replace(ana, List.of(), 2, OPENED.plusSeconds(8));
    assertThat(room.boardFor(bruno).content().version()).isEqualTo(3);
    assertThat(room.boardFor(bruno).hasActivity()).isTrue();
  }

  @Test
  void theBoardKeepsTheLimitsOfEveryBoard() {
    PracticeRoom room = bothIn();
    for (int i = 0; i < 2000; i++) {
      room.draw(ana, put("e" + i), OPENED.plusSeconds(30));
    }
    assertThatThrownBy(() -> room.draw(ana, put("one-too-many"), OPENED.plusSeconds(31)))
        .isInstanceOf(WhiteboardRuleViolation.class);
  }

  @Test
  void anySixDigitCodeConfirmsPresenceOnceBothAreIn() {
    PracticeRoom room = aRoom();
    room.enter(ana, OPENED);
    assertThatThrownBy(() -> room.confirmPresence(ana, OPENED))
        .isInstanceOf(PresenceCheckUnavailable.class);
    assertThatThrownBy(() -> room.requireResend(ana)).isInstanceOf(PresenceResendRefused.class);

    room.enter(bruno, OPENED.plusSeconds(5));
    room.requireResend(ana);
    assertThat(room.confirmPresence(ana, OPENED.plusSeconds(6))).isEqualTo(OPENED.plusSeconds(6));
    assertThat(room.confirmPresence(ana, OPENED.plusSeconds(9))).isEqualTo(OPENED.plusSeconds(6));
    assertThatThrownBy(() -> room.requireResend(ana)).isInstanceOf(PresenceResendRefused.class);
    assertThatThrownBy(() -> room.confirmPresence(carla, OPENED))
        .isInstanceOf(NotAParticipant.class);
  }

  @Test
  void theFirstToEndWaitsAndTheSecondEndsIt() {
    PracticeRoom room = bothIn();
    assertThat(room.confirmEnd(ana, OPENED.plusSeconds(60))).isFalse();
    assertThat(room.confirmEnd(ana, OPENED.plusSeconds(61))).isFalse();
    assertThat(room.endConfirmedAtOf(ana)).isEqualTo(OPENED.plusSeconds(60));
    assertThat(room.status()).isEqualTo(SessionStatus.IN_PROGRESS);

    assertThat(room.confirmEnd(bruno, OPENED.plusSeconds(70))).isTrue();
    assertThat(room.status()).isEqualTo(SessionStatus.COMPLETED);
    assertThat(room.getEndedAt()).isEqualTo(OPENED.plusSeconds(70));
    assertThatThrownBy(() -> room.draw(ana, put("late"), OPENED.plusSeconds(71)))
        .isInstanceOf(SessionNotOpen.class);
  }

  @Test
  void aRoomNotInProgressCannotBeEnded() {
    PracticeRoom room = aRoom();
    room.enter(ana, OPENED);
    assertThatThrownBy(() -> room.confirmEnd(ana, OPENED)).isInstanceOf(SessionNotOpen.class);
    assertThatThrownBy(() -> room.confirmEnd(carla, OPENED)).isInstanceOf(NotAParticipant.class);
  }

  @Test
  void itsLifetimeEndsFourHoursAfterItWasOpened() {
    PracticeRoom room = aRoom();
    assertThat(room.getClosesAt()).isEqualTo(OPENED.plus(Duration.ofHours(4)));
    assertThat(room.isExpiredAt(OPENED.plus(Duration.ofHours(4)).minusMillis(1))).isFalse();
    assertThat(room.isExpiredAt(OPENED.plus(Duration.ofHours(4)))).isTrue();
  }
}
