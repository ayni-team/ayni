package pe.ayni.config.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whiteboards of the demo history, in the two formats a board may hold (ADR 0010): one drawn
 * before Excalidraw, with the old types the web still reads, and one drawn with Excalidraw, whose
 * elements are Excalidraw's own JSON. The backend stores both as they come and never reads them.
 */
final class DemoBoards {

  /** An element of a board, and whether the tutor drew it (otherwise, the student). */
  record Drawn(String id, String type, Map<String, Object> data, boolean byTutor) {}

  private DemoBoards() {}

  /** What a tutor and a student drew before Excalidraw: texts and a stroke of the old format. */
  static List<Drawn> oldFormat() {
    return List.of(
        new Drawn(
            "title",
            "text",
            Map.of("text", "Resumen de la sesión", "x", 40, "y", 40, "color", "#1F2937"),
            true),
        new Drawn(
            "steps",
            "text",
            Map.of(
                "text", "1. Identificar el problema\n2. Plantear un ejemplo\n3. Resolverlo juntos",
                "x", 40, "y", 100, "color", "#1F2937"),
            true),
        new Drawn(
            "underline",
            "stroke",
            Map.of(
                "points", List.of(List.of(40, 70), List.of(260, 70)),
                "color", "#E42322",
                "width", 3),
            false),
        new Drawn(
            "question",
            "text",
            Map.of(
                "text", "¿Y si el caso tiene dos excepciones?",
                "x", 40, "y", 220, "color", "#2563EB"),
            false));
  }

  /**
   * Inheritance and an interface, drawn with Excalidraw: two classes with their labels, an
   * interface, the arrows between them, the student's stroke and question.
   */
  static List<Drawn> excalidraw() {
    List<Drawn> board = new ArrayList<>();
    board.add(text("hx-title", 40, 24, "Herencia e interfaces", 28, "#1e1e1e", null, "a0", true));
    board.add(box("hx-animal", "rectangle", 60, 100, 200, 70, "a1", "hx-animal-label", true));
    board.add(label("hx-animal-label", "hx-animal", 60, 100, 200, 70, "Animal", "a2", true));
    board.add(box("hx-dog", "rectangle", 60, 290, 200, 70, "a3", "hx-dog-label", true));
    board.add(label("hx-dog-label", "hx-dog", 60, 290, 200, 70, "Perro", "a4", true));
    Map<String, Object> swimmer =
        element("hx-swimmer", "ellipse", 360, 95, 240, 80, "a5", List.of(binding("hx-swimmer-label", "text")));
    swimmer.put("strokeStyle", "dashed");
    board.add(new Drawn("hx-swimmer", "ellipse", swimmer, true));
    board.add(label("hx-swimmer-label", "hx-swimmer", 360, 95, 240, 80, "«interfaz» Nadador", "a6", true));
    board.add(arrow("hx-extends", 160, 286, List.of(List.of(0, 0), List.of(0, -112)), "hx-dog", "hx-animal", "solid", "a7"));
    board.add(arrow("hx-implements", 250, 290, List.of(List.of(0, 0), List.of(170, -112)), "hx-dog", "hx-swimmer", "dashed", "a8"));
    board.add(stroke("hx-circle", 350, 82, "a9"));
    board.add(text("hx-question", 40, 400, "¿Un perro puede implementar dos interfaces?", 20, "#1971c2", null, "aA", false));
    return board;
  }

  private static Map<String, Object> element(
      String id, String type, int x, int y, int width, int height, String index, List<Map<String, Object>> bound) {
    Map<String, Object> element = new LinkedHashMap<>();
    element.put("id", id);
    element.put("type", type);
    element.put("x", x);
    element.put("y", y);
    element.put("width", width);
    element.put("height", height);
    element.put("angle", 0);
    element.put("strokeColor", "#1e1e1e");
    element.put("backgroundColor", "transparent");
    element.put("fillStyle", "solid");
    element.put("strokeWidth", 2);
    element.put("strokeStyle", "solid");
    element.put("roughness", 0);
    element.put("opacity", 100);
    element.put("groupIds", List.of());
    element.put("frameId", null);
    element.put("roundness", type.equals("rectangle") ? Map.of("type", 3) : null);
    element.put("seed", Math.abs(id.hashCode()) + 1);
    element.put("version", 1);
    element.put("versionNonce", Math.abs((id + "-nonce").hashCode()) + 1);
    element.put("index", index);
    element.put("isDeleted", false);
    element.put("boundElements", bound);
    element.put("updated", 0);
    element.put("link", null);
    element.put("locked", false);
    return element;
  }

  private static Map<String, Object> binding(String id, String type) {
    return Map.of("id", id, "type", type);
  }

  private static Drawn box(
      String id, String type, int x, int y, int width, int height, String index, String label, boolean byTutor) {
    Map<String, Object> box = element(id, type, x, y, width, height, index, List.of(binding(label, "text")));
    box.put("backgroundColor", "#e7f5ff");
    return new Drawn(id, type, box, byTutor);
  }

  private static Drawn text(
      String id, int x, int y, String text, int size, String color, String containerId, String index, boolean byTutor) {
    int width = (int) Math.ceil(text.length() * size * 0.55);
    int height = (int) Math.ceil(size * 1.25);
    Map<String, Object> element = element(id, "text", x, y, width, height, index, null);
    element.put("strokeColor", color);
    element.put("fontSize", size);
    element.put("fontFamily", 6);
    element.put("text", text);
    element.put("originalText", text);
    element.put("textAlign", containerId == null ? "left" : "center");
    element.put("verticalAlign", containerId == null ? "top" : "middle");
    element.put("containerId", containerId);
    element.put("autoResize", true);
    element.put("lineHeight", 1.25);
    return new Drawn(id, "text", element, byTutor);
  }

  /** A label centred in its box, as Excalidraw lays out a bound text. */
  private static Drawn label(
      String id, String container, int x, int y, int width, int height, String text, String index, boolean byTutor) {
    int size = 20;
    int textWidth = (int) Math.ceil(text.length() * size * 0.55);
    int textHeight = (int) Math.ceil(size * 1.25);
    return text(id, x + (width - textWidth) / 2, y + (height - textHeight) / 2, text, size, "#1e1e1e", container, index, byTutor);
  }

  private static Drawn arrow(
      String id, int x, int y, List<List<Integer>> points, String from, String to, String style, String index) {
    List<Integer> end = points.get(points.size() - 1);
    Map<String, Object> arrow = element(id, "arrow", x, y, Math.abs(end.get(0)), Math.abs(end.get(1)), index, null);
    arrow.put("strokeStyle", style);
    arrow.put("points", points);
    arrow.put("lastCommittedPoint", null);
    arrow.put("startBinding", Map.of("elementId", from, "focus", 0, "gap", 4));
    arrow.put("endBinding", Map.of("elementId", to, "focus", 0, "gap", 4));
    arrow.put("startArrowhead", null);
    arrow.put("endArrowhead", "triangle_outline");
    arrow.put("elbowed", false);
    return new Drawn(id, "arrow", arrow, true);
  }

  /** The student circles the interface by hand. */
  private static Drawn stroke(String id, int x, int y, String index) {
    List<List<Double>> points = new ArrayList<>();
    for (int step = 0; step <= 36; step++) {
      double angle = Math.toRadians(step * 10.5);
      double px = Math.round((130 + 140 * Math.cos(angle) - 140) * 100) / 100.0;
      double py = Math.round((52 + 56 * Math.sin(angle)) * 100) / 100.0;
      points.add(List.of(px + 140, py));
    }
    Map<String, Object> stroke = element(id, "freedraw", x, y, 270, 108, index, null);
    stroke.put("strokeColor", "#e03131");
    stroke.put("points", points);
    stroke.put("pressures", List.of());
    stroke.put("simulatePressure", true);
    stroke.put("lastCommittedPoint", null);
    return new Drawn(id, "freedraw", stroke, false);
  }
}
