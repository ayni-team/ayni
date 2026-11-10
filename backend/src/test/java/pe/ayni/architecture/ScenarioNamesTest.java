package pe.ayni.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every scenario of a {@code .feature} that is not tagged {@code @pending} has a test whose display
 * name is exactly the name of the scenario (backend-guide.md, "Definition of done").
 *
 * <p>It reads the sources, not the compiled tests: the feature files and the test classes of this
 * module, from the directory Maven runs the tests in. A scenario renamed on one side only, or a
 * test that lost its display name, fails here with the file and the name to fix. The same list,
 * printed instead of asserted, is what was used to align the existing ones.
 */
class ScenarioNamesTest {

  private static final Path FEATURES = Path.of("src/test/resources/features");
  private static final Path TESTS = Path.of("src/test/java");

  /** The argument of a display name: one string literal or several joined with {@code +}. */
  private static final Pattern DISPLAY_NAME =
      Pattern.compile("@DisplayName\\(\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+)\\)");

  private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

  private static final Pattern SCENARIO =
      Pattern.compile("^(?:Scenario|Scenario Outline|Scenario Template|Example):\\s*(.*)$");

  @Test
  @DisplayName("every scenario that is not pending has a test with its exact name")
  void everyScenarioThatIsNotPendingHasATestWithItsName() throws IOException {
    Set<String> displayNames = displayNames();

    List<String> missing = new ArrayList<>();
    for (Path feature : files(FEATURES, ".feature")) {
      for (String scenario : scenariosToRun(feature)) {
        if (!displayNames.contains(scenario)) {
          missing.add(FEATURES.relativize(feature) + ": " + scenario);
        }
      }
    }

    assertThat(missing)
        .as("scenarios without a test of the same display name")
        .isEmpty();
  }

  @Test
  @DisplayName("the check reads the feature files and the tests it is meant to compare")
  void theCheckReadsWhatItCompares() throws IOException {
    // Run from another directory the check would find nothing and pass for the wrong reason.
    assertThat(files(FEATURES, ".feature")).hasSizeGreaterThan(50);
    assertThat(displayNames()).contains("Successful booking");
  }

  private static Set<String> displayNames() throws IOException {
    Set<String> names = new HashSet<>();
    for (Path test : files(TESTS, ".java")) {
      Matcher displayName = DISPLAY_NAME.matcher(Files.readString(test));
      while (displayName.find()) {
        StringBuilder name = new StringBuilder();
        Matcher literal = LITERAL.matcher(displayName.group(1));
        while (literal.find()) {
          name.append(unescape(literal.group(1)));
        }
        names.add(name.toString());
      }
    }
    return names;
  }

  /** The scenarios of a feature that are not pending, by themselves or by their feature. */
  static List<String> scenariosToRun(Path feature) throws IOException {
    List<String> scenarios = new ArrayList<>();
    List<String> tags = new ArrayList<>();
    boolean featurePending = false;
    for (String raw : Files.readAllLines(feature)) {
      String line = raw.strip();
      if (line.startsWith("@")) {
        tags.addAll(List.of(line.split("\\s+")));
        continue;
      }
      if (line.startsWith("Feature:")) {
        featurePending = tags.contains("@pending");
        tags.clear();
        continue;
      }
      Matcher scenario = SCENARIO.matcher(line);
      if (scenario.matches()) {
        if (!featurePending && !tags.contains("@pending")) {
          scenarios.add(scenario.group(1).strip());
        }
        tags.clear();
      } else if (!line.isEmpty() && !line.startsWith("#")) {
        tags.clear();
      }
    }
    return scenarios;
  }

  private static List<Path> files(Path root, String extension) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      return paths.filter(path -> path.toString().endsWith(extension)).sorted().toList();
    }
  }

  private static String unescape(String literal) {
    return literal.replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\");
  }
}
