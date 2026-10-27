package pe.ayni.sessions.application;

import java.util.List;

/**
 * The service that prepares the support material of a session from the student's need (US07).
 *
 * <p>A port: today a simulated adapter answers it; a real one, a language model or a library of
 * materials, replaces it without touching the use case.
 */
public interface SupportMaterialGenerator {

  /**
   * @param needDescription what the student wrote when booking
   * @throws SupportMaterialUnavailable when the service cannot answer now
   */
  Material prepare(String needDescription);

  /** What the service answers: a summary of the need and what the tutor could prepare. */
  record Material(String summary, List<String> suggestions) {}

  /** The service did not answer; the session shows only the need. */
  class SupportMaterialUnavailable extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SupportMaterialUnavailable(String message) {
      super(message);
    }
  }
}
