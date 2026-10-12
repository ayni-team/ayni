package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** US42: when two names are the same skill under another spelling, and when they are not. */
class NameSimilarityTest {

  @ParameterizedTest(name = "\"{0}\" and \"{1}\" look alike")
  @DisplayName("names that are the same skill written another way look alike")
  @CsvSource(
      delimiter = '|',
      value = {
        "Python|python",
        "Python|  PYTHON  ",
        "Node.js|NodeJS",
        "Node.js|Node JS",
        "Postgre SQL|PostgreSQL",
        "Programación|Programacion",
        "Pyhton|Python",
        "Cálculo I|Cálculo II",
        "Exel|Excel",
        "Programación en Python|Python",
        "Python|Introduction to Python",
        "Diseño de interfaces|Diseño interfaces",
        "Diseño de interfaces|Diseño de interfaz",
        "Machine Learning|Learning Machine",
        "Adobe Photoshop|Photoshop",
        "Java|JavaScript"
      })
  void namesThatAreTheSameSkillLookAlike(String first, String second) {
    assertThat(NameSimilarity.areAlike(first, second)).isTrue();
    assertThat(NameSimilarity.areAlike(second, first)).isTrue();
  }

  @ParameterizedTest(name = "\"{0}\" and \"{1}\" do not look alike")
  @DisplayName("names of different skills do not look alike")
  @CsvSource(
      delimiter = '|',
      value = {
        "Python|Figma",
        "Python|Photoshop",
        "Docker|Kubernetes",
        "Databases|Data Structures",
        "C++|C#",
        "Git|Go",
        "Illustrator|Excel",
        "Diseño de interfaces|Diseño de bases de datos"
      })
  void namesOfDifferentSkillsDoNotLookAlike(String first, String second) {
    assertThat(NameSimilarity.areAlike(first, second)).isFalse();
    assertThat(NameSimilarity.areAlike(second, first)).isFalse();
  }

  @org.junit.jupiter.api.Test
  @DisplayName("the same name scores one and a name with nothing in common scores below the threshold")
  void theSameNameScoresOne() {
    assertThat(NameSimilarity.between("Figma", "figma")).isEqualTo(1.0);
    assertThat(NameSimilarity.between("Figma", "Docker")).isLessThan(NameSimilarity.THRESHOLD);
  }

  @org.junit.jupiter.api.Test
  @DisplayName("a missing or empty name looks like nothing")
  void aMissingNameLooksLikeNothing() {
    assertThat(NameSimilarity.between(null, "Python")).isZero();
    assertThat(NameSimilarity.between("   ", "Python")).isZero();
    assertThat(NameSimilarity.between("!!!", "Python")).isZero();
  }

  @org.junit.jupiter.api.Test
  @DisplayName("the symbols of a tool's name are not lost: C++ and C# are not just C")
  void theSymbolsOfAToolsNameAreNotLost() {
    assertThat(NameSimilarity.normalise("C++")).isEqualTo("cplusplus");
    assertThat(NameSimilarity.normalise("C#")).isEqualTo("csharp");
    assertThat(NameSimilarity.normalise("  Programación, avanzada! ")).isEqualTo("programacion avanzada");
  }
}
