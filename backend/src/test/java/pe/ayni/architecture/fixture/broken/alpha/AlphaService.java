package pe.ayni.architecture.fixture.broken.alpha;

import pe.ayni.architecture.fixture.broken.beta.internal.BetaSecret;

/**
 * Tests only: a module that breaks the rule on purpose, reaching into beta's internals, so that
 * {@code ModuleExtractionTest} can show the build would name it (TS09, scenario 3).
 */
public class AlphaService {
  public String use() {
    return new BetaSecret().reveal();
  }
}
