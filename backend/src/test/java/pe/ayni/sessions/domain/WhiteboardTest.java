package pe.ayni.sessions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.domain.model.BoardContent;
import pe.ayni.sessions.domain.model.BoardElement;
import pe.ayni.sessions.domain.model.BoardOperation;
import pe.ayni.sessions.domain.model.BoardOperation.Action;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.domain.model.Whiteboard;
import pe.ayni.sessions.domain.model.WhiteboardRuleViolation;
import pe.ayni.sessions.domain.model.WhiteboardVersionConflict;

/** The rules of the whiteboard (US10, ADR 0009), without Spring. */
class WhiteboardTest {

  private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");

  private Whiteboard board;

  @BeforeEach
  void anEmptyBoard() {
    Session session =
        Session.schedule(
            UUID.randomUUID(),
            "UPC",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            NOW,
            NOW.plus(Duration.ofHours(1)),
            NOW.minus(Duration.ofDays(1)));
    board = Whiteboard.emptyFor(session, NOW);
  }

  private static BoardElement stroke(String id, Object marker) {
    return new BoardElement(id, "stroke", Map.of("marker", marker));
  }

  private static BoardOperation put(String id, Object marker) {
    return new BoardOperation("op-" + UUID.randomUUID(), Action.PUT_ELEMENT, stroke(id, marker), null);
  }

  private static BoardOperation remove(String id) {
    return new BoardOperation("op-" + UUID.randomUUID(), Action.REMOVE_ELEMENT, null, id);
  }

  private List<String> ids() {
    return board.content().elements().stream().map(BoardElement::id).toList();
  }

  @Test
  @DisplayName("an empty board has version 0 and no activity")
  void anEmptyBoard_() {
    assertThat(board.content()).isEqualTo(BoardContent.EMPTY);
    assertThat(board.hasActivity()).isFalse();
  }

  @Test
  @DisplayName("every operation gives the next version and marks activity")
  void everyOperationGivesTheNextVersion() {
    assertThat(board.apply(put("a", 1), NOW)).isEqualTo(1);
    assertThat(board.apply(put("b", 1), NOW.plusSeconds(1))).isEqualTo(2);
    assertThat(board.apply(remove("a"), NOW.plusSeconds(2))).isEqualTo(3);

    assertThat(ids()).containsExactly("b");
    assertThat(board.hasActivity()).isTrue();
    assertThat(board.getUpdatedAt()).isEqualTo(NOW.plusSeconds(2));
  }

  @Test
  @DisplayName("replacing an element keeps its place and takes the later version's content")
  void theLaterVersionWins() {
    board.apply(put("a", "first"), NOW);
    board.apply(put("b", "first"), NOW);
    board.apply(put("a", "second"), NOW);

    assertThat(ids()).containsExactly("a", "b");
    assertThat(board.content().elements().getFirst().data()).containsEntry("marker", "second");
  }

  @Test
  @DisplayName("every action applied twice leaves the board as applied once")
  void everyActionIsIdempotent() {
    BoardOperation put = put("a", 1);
    board.apply(put, NOW);
    List<BoardElement> once = board.content().elements();
    board.apply(put, NOW);
    assertThat(board.content().elements()).isEqualTo(once);

    board.apply(remove("a"), NOW);
    board.apply(remove("a"), NOW);
    assertThat(ids()).isEmpty();

    BoardOperation clear = new BoardOperation("op-c", Action.CLEAR, null, null);
    board.apply(put("b", 1), NOW);
    board.apply(clear, NOW);
    board.apply(clear, NOW);
    assertThat(ids()).isEmpty();
  }

  @Test
  @DisplayName("a whole board replaces only the version it saw")
  void replacingNeedsTheCurrentVersion() {
    board.apply(put("a", 1), NOW);

    assertThatThrownBy(() -> board.replace(List.of(stroke("z", 1)), 0, NOW))
        .isInstanceOf(WhiteboardVersionConflict.class)
        .satisfies(
            conflict -> assertThat(((WhiteboardVersionConflict) conflict).currentVersion()).isOne());

    assertThat(board.replace(List.of(stroke("z", 1), stroke("y", 2)), 1, NOW)).isEqualTo(2);
    assertThat(ids()).containsExactly("z", "y");
  }

  @Test
  @DisplayName("a board holds at most 2000 elements")
  void aBoardHasALimit() {
    List<BoardElement> full = new ArrayList<>();
    for (int i = 0; i < BoardContent.MAX_ELEMENTS; i++) {
      full.add(stroke("e" + i, i));
    }
    board.replace(full, 0, NOW);

    assertThatThrownBy(() -> board.apply(put("one-more", 1), NOW))
        .isInstanceOf(WhiteboardRuleViolation.class);
    assertThat(board.content().elements()).hasSize(BoardContent.MAX_ELEMENTS);
    // Replacing one that is already there is fine.
    board.apply(put("e0", "again"), NOW);
  }

  @Test
  @DisplayName("an element needs a valid id and a type, and an operation what its action needs")
  void malformedElementsAndOperationsAreRefused() {
    assertThatThrownBy(() -> new BoardElement("has spaces", "stroke", null))
        .isInstanceOf(WhiteboardRuleViolation.class);
    assertThatThrownBy(() -> new BoardElement("x".repeat(65), "stroke", null))
        .isInstanceOf(WhiteboardRuleViolation.class);
    assertThatThrownBy(() -> new BoardElement("ok", " ", null))
        .isInstanceOf(WhiteboardRuleViolation.class);
    assertThatThrownBy(() -> new BoardOperation("op", Action.PUT_ELEMENT, null, null))
        .isInstanceOf(WhiteboardRuleViolation.class);
    assertThatThrownBy(() -> new BoardOperation("op", Action.REMOVE_ELEMENT, null, null))
        .isInstanceOf(WhiteboardRuleViolation.class);
    assertThatThrownBy(() -> new BoardOperation("op", null, null, null))
        .isInstanceOf(WhiteboardRuleViolation.class);
    assertThat(new BoardElement("ok", "text", null).data()).isEmpty();
  }
}
