package pe.ayni.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import pe.ayni.AyniApplication;

/**
 * Verifies that the modules stay independent of one another.
 *
 * <p>This is the only architecture test in the project, and it is worth its three lines: it fails
 * the build when a module reaches into another module's internals instead of going through its
 * events or the interface it publishes. Without it, the boundaries are an agreement that erodes on
 * the first hurried pull request.
 */
class ModularityTest {

  private final ApplicationModules modules = ApplicationModules.of(AyniApplication.class);

  /** Fails when a module depends on the internals of another one. */
  @Test
  void verifiesModularStructure() {
    modules.verify();
  }

  /**
   * Writes module diagrams under {@code target/spring-modulith-docs}.
   *
   * <p>They are generated from the code, so they cannot drift from what was actually built, which
   * makes them good evidence for the architecture chapter of the report.
   */
  @Test
  void writesDocumentation() {
    new Documenter(modules).writeDocumentation().writeIndividualModulesAsPlantUml();
  }
}
