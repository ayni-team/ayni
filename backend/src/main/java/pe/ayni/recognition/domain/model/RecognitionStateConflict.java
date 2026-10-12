package pe.ayni.recognition.domain.model;

/**
 * The request is well formed, but the state of things refuses it: the university has not opened
 * recognition, or the student has not taught enough hours yet. Answers 409.
 */
public class RecognitionStateConflict extends RuntimeException {

  public RecognitionStateConflict(String message) {
    super(message);
  }
}
