package pe.ayni.sessions.domain.model;

/**
 * A change to a whiteboard, as a participant sends it (ADR 0009).
 *
 * <p>Every action is idempotent: applying the same operation twice leaves the board as applying it
 * once, so a client that resends it after a dropped connection does no harm.
 *
 * @param id chosen by the client, so it recognises its own operation when the server sends it back
 * @param element the element {@code PUT_ELEMENT} adds or replaces
 * @param elementId the element {@code REMOVE_ELEMENT} removes
 */
public record BoardOperation(String id, Action action, BoardElement element, String elementId) {

  public enum Action {
    /** Adds the element, or replaces the one with the same id. */
    PUT_ELEMENT,
    /** Removes the element with that id, if it is there. */
    REMOVE_ELEMENT,
    /** Removes every element. */
    CLEAR
  }

  /** @throws WhiteboardRuleViolation when the operation lacks what its action needs */
  public BoardOperation {
    BoardElement.requireId(id);
    if (action == null) {
      throw new WhiteboardRuleViolation("An operation needs an action");
    }
    if (action == Action.PUT_ELEMENT && element == null) {
      throw new WhiteboardRuleViolation("PUT_ELEMENT needs the element");
    }
    if (action == Action.REMOVE_ELEMENT) {
      BoardElement.requireId(elementId);
    }
  }
}
