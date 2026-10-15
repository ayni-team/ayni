package pe.ayni.sessions.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.sessions.application.SupportMaterialGenerator;
import pe.ayni.sessions.application.SupportMaterialGenerator.Material;

/** The simulated service that prepares support material (US07), without Spring. */
class SimulatedSupportMaterialGeneratorTest {

  @Test
  @DisplayName("summarises the need with its first sentence and suggests from its main words")
  void preparesFromTheNeed() {
    Material material =
        new SimulatedSupportMaterialGenerator("AVAILABLE")
            .prepare("Quiero repasar integrales por partes. Tengo examen el lunes.");

    assertThat(material.summary()).isEqualTo("Quiero repasar integrales por partes.");
    assertThat(material.suggestions())
        .containsExactly(
            "Prepara un ejemplo resuelto sobre integrales",
            "Prepara un ejemplo resuelto sobre partes",
            "Pide al estudiante que traiga el ejercicio que no pudo resolver");
  }

  @Test
  @DisplayName("an empty need still gets a summary that says so")
  void anEmptyNeed() {
    assertThat(new SimulatedSupportMaterialGenerator("AVAILABLE").prepare(" ").summary())
        .isEqualTo("El estudiante no describió lo que necesita");
  }

  @Test
  @DisplayName("can be told to be unavailable")
  void canBeUnavailable() {
    assertThatThrownBy(() -> new SimulatedSupportMaterialGenerator("UNAVAILABLE").prepare("x"))
        .isInstanceOf(SupportMaterialGenerator.SupportMaterialUnavailable.class);
  }
}
