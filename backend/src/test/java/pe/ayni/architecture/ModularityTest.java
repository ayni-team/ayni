package pe.ayni.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import pe.ayni.AyniApplication;

class ModularityTest {

  private final ApplicationModules modules = ApplicationModules.of(AyniApplication.class);

  @Test
  void verifiesModularStructure() {
    modules.verify();
  }

  @Test
  void writesDocumentation() {
    new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
  }
}