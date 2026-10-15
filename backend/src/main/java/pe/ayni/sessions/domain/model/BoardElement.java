package pe.ayni.sessions.domain.model;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * One thing drawn on a whiteboard: a stroke, a text, a shape (ADR 0009).
 *
 * <p>The backend stores {@code data} as the client sent it and never reads it. It only cares that
 * the element can be told apart from the others, by its {@code id}, and what kind of thing it is.
 *
 * @param id chosen by the client that drew it: 1 to 64 letters, digits, {@code -} or {@code _}
 * @param type what it is, for the client to draw it: 1 to 32 characters
 */
public record BoardElement(String id, String type, Map<String, Object> data) {

  private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  /** @throws WhiteboardRuleViolation when the id or the type is missing or does not fit */
  public BoardElement {
    requireId(id);
    if (type == null || type.isBlank() || type.length() > 32) {
      throw new WhiteboardRuleViolation("An element needs a type of 1 to 32 characters");
    }
    data = data == null ? Map.of() : data;
  }

  static void requireId(String id) {
    if (id == null || !ID.matcher(id).matches()) {
      throw new WhiteboardRuleViolation(
          "An element id has 1 to 64 letters, digits, hyphens or underscores");
    }
  }
}
