package pe.ayni.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import pe.ayni.AyniApplication;

/**
 * TS09: extracting a module does not force changes in the others (ADR 0002). Scenarios 1, 3 and 4
 * of {@code features/TS09-module-extraction.feature}, with the same names; scenario 2, the
 * database, is {@link SchemaBoundaryTest}.
 */
class ModuleExtractionTest {

  /** The packages of Ayni that are not modules: shared wiring and building blocks (ADR 0003). */
  private static final Set<String> NOT_MODULES = Set.of("config", "shared");

  @Test
  @DisplayName("Isolation in the code")
  void isolationInTheCode() {
    Violations violations = ApplicationModules.of(AyniApplication.class).detectViolations();

    assertThat(violations.getMessages()).isEmpty();
  }

  @Test
  @DisplayName("Verification on every integration")
  void verificationOnEveryIntegration() {
    // A fixture of two modules in the test sources, where alpha reaches into beta's internals.
    ApplicationModules broken =
        ApplicationModules.of("pe.ayni.architecture.fixture.broken", location -> true);

    Violations violations = broken.detectViolations();

    assertThat(violations.hasViolations()).isTrue();
    assertThat(violations.getMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("alpha")
                    .contains("beta")
                    .contains("BetaSecret"));
  }

  @Test
  @DisplayName("The scope of a change is bounded")
  void theScopeOfAChangeIsBounded() {
    JavaClasses ayni =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("pe.ayni");

    // What one module uses of another is only what the other publishes at its root: its *Api and
    // the views those answer. Everything below the root can change without the others noticing.
    List<Dependency> across =
        ayni.stream()
            .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
            .filter(ModuleExtractionTest::crossesModules)
            .toList();
    // Modules do talk to each other (booking charges through WalletApi, for one): the check has
    // something to look at.
    assertThat(across)
        .anySatisfy(
            dependency ->
                assertThat(dependency.getTargetClass().getName())
                    .isEqualTo("pe.ayni.wallet.WalletApi"));

    List<String> reachingInside =
        across.stream()
            .filter(dependency -> !isRootOfItsModule(dependency.getTargetClass()))
            .map(Dependency::getDescription)
            .distinct()
            .toList();

    assertThat(reachingInside).isEmpty();
  }

  private static boolean crossesModules(Dependency dependency) {
    String from = moduleOf(dependency.getOriginClass());
    String to = moduleOf(dependency.getTargetClass());
    return from != null && to != null && !from.equals(to);
  }

  /** The module a class of Ayni belongs to, or null for anything else. */
  private static String moduleOf(JavaClass type) {
    String name = type.getPackageName();
    if (!name.startsWith("pe.ayni.")) {
      return null;
    }
    String module = name.substring("pe.ayni.".length()).split("\\.")[0];
    return NOT_MODULES.contains(module) ? null : module;
  }

  private static boolean isRootOfItsModule(JavaClass type) {
    return type.getPackageName().split("\\.").length == 3;
  }
}
