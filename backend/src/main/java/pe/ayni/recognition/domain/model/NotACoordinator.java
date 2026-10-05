package pe.ayni.recognition.domain.model;

/**
 * Reviewing recognition requests is for the coordinators of the university: the person is known
 * there but is not one. Answers 403.
 */
public class NotACoordinator extends RuntimeException {

  public NotACoordinator() {
    super("only a coordinator of the university can review recognition requests");
  }
}
