package pe.ayni.sessions.infrastructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.ayni.sessions.application.SupportMaterialGenerator;

/**
 * A stand in for the service that prepares support material (US07), so the flow can be built and
 * tested before there is a real one.
 *
 * <p>It answers deterministically from the need itself: the first sentence as the summary, and a
 * suggestion for each of the main words of the need. In Spanish, like everything the tutor reads
 * in the web application. {@code
 * ayni.sessions.support-material.simulated-outcome=UNAVAILABLE} makes it fail, which is how the
 * case of a service that does not answer is exercised; the default is {@code AVAILABLE}.
 */
@Component
class SimulatedSupportMaterialGenerator implements SupportMaterialGenerator {

  /** Words that say nothing about the topic, in the two languages students write in. */
  private static final Set<String> FILLER =
      Set.of(
          "para", "como", "with", "about", "that", "this", "the", "and", "una", "unos", "unas",
          "quiero", "necesito", "ayuda", "help", "need", "want", "antes", "before", "del", "las",
          "los", "por", "que", "con", "sobre", "entender", "understand", "repasar", "review",
          "tengo", "tiene", "hasta", "entiendo", "examen", "exam", "lunes", "martes", "jueves",
          "viernes", "domingo", "sabado", "miercoles", "manana", "semana", "clase", "curso");

  private final String outcome;

  SimulatedSupportMaterialGenerator(
      @Value("${ayni.sessions.support-material.simulated-outcome:AVAILABLE}") String outcome) {
    this.outcome = outcome;
  }

  @Override
  public Material prepare(String needDescription) {
    if ("UNAVAILABLE".equalsIgnoreCase(outcome)) {
      throw new SupportMaterialUnavailable("The simulated support material service is unavailable");
    }
    String need = needDescription == null ? "" : needDescription.strip();
    String summary = firstSentence(need);
    List<String> suggestions = new ArrayList<>();
    for (String topic : topicsOf(need)) {
      suggestions.add("Prepara un ejemplo resuelto sobre " + topic);
    }
    suggestions.add("Pide al estudiante que traiga el ejercicio que no pudo resolver");
    return new Material(summary.isEmpty() ? "El estudiante no describió lo que necesita" : summary,
        suggestions);
  }

  private static String firstSentence(String need) {
    int end = need.length();
    for (char stop : new char[] {'.', '?', '!', '\n'}) {
      int at = need.indexOf(stop);
      if (at > 0 && at < end) {
        end = at + (stop == '\n' ? 0 : 1);
      }
    }
    return need.substring(0, end).strip();
  }

  /** Up to three of the longer words of the need, in the order they appear. */
  private static List<String> topicsOf(String need) {
    Set<String> topics = new LinkedHashSet<>();
    Arrays.stream(need.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
        .filter(word -> word.length() > 3)
        .filter(word -> !FILLER.contains(word))
        .forEach(topics::add);
    return topics.stream().limit(3).toList();
  }
}
