package pe.ayni.recognition.domain.model;

/** A rule of recognition refusing what was asked, such as a decision without a reason. Answers 400. */
public class RecognitionRuleViolation extends RuntimeException {

  public RecognitionRuleViolation(String message) {
    super(message);
  }
}
