package pe.ayni.recognition.domain.model;

/**
 * The student has not taught the hours their university asks for, so nothing is registered. It says
 * how many are missing, which is what the student needs to know to plan the next sessions.
 */
public class InsufficientHours extends RecognitionStateConflict {

  private final int missingHours;

  public InsufficientHours(int missingHours) {
    super(
        "%d more %s needed before the recognition can be requested"
            .formatted(missingHours, missingHours == 1 ? "hour is" : "hours are"));
    this.missingHours = missingHours;
  }

  public int getMissingHours() {
    return missingHours;
  }
}
