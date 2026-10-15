package pe.ayni.sessions.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a whiteboard holds: its elements, in the order they were first drawn, and how many
 * operations it has applied (ADR 0009).
 *
 * <p>Immutable: applying an operation answers a new content with the next version. The version is
 * the order the server gave the operation, the one both participants apply it in.
 */
public record BoardContent(long version, List<BoardElement> elements) {

  /** The most elements a board holds. */
  public static final int MAX_ELEMENTS = 2000;

  public static final BoardContent EMPTY = new BoardContent(0, List.of());

  public BoardContent {
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
    elements = elements == null ? List.of() : List.copyOf(elements);
  }

  /** @throws WhiteboardRuleViolation when the board would hold more than {@link #MAX_ELEMENTS} */
  BoardContent apply(BoardOperation operation) {
    Map<String, BoardElement> byId = new LinkedHashMap<>();
    elements.forEach(element -> byId.put(element.id(), element));
    switch (operation.action()) {
      case PUT_ELEMENT -> byId.put(operation.element().id(), operation.element());
      case REMOVE_ELEMENT -> byId.remove(operation.elementId());
      case CLEAR -> byId.clear();
    }
    return next(new ArrayList<>(byId.values()));
  }

  /** @throws WhiteboardRuleViolation when the board would hold more than {@link #MAX_ELEMENTS} */
  BoardContent replacedBy(List<BoardElement> replacement) {
    Map<String, BoardElement> byId = new LinkedHashMap<>();
    replacement.forEach(element -> byId.put(element.id(), element));
    return next(new ArrayList<>(byId.values()));
  }

  private BoardContent next(List<BoardElement> elements) {
    if (elements.size() > MAX_ELEMENTS) {
      throw new WhiteboardRuleViolation(
          "A whiteboard holds at most " + MAX_ELEMENTS + " elements");
    }
    return new BoardContent(version + 1, elements);
  }
}
