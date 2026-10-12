package pe.ayni.recognition.domain.model;

/**
 * How far a student is from the hours their university asks for.
 *
 * <p>The hours are those of verified sessions the student taught, which are the credits earned by
 * teaching: one credit is one booked hour. Credits a university assigned or a student bought are
 * never part of it, because no session stands behind them.
 *
 * @param earnedHours hours taught that no request has used yet
 * @param sessionsCount sessions those hours come from
 * @param requiredHours what the university asks for, or {@code null} when it has no rule in force
 */
public record RecognitionProgress(int earnedHours, int sessionsCount, Integer requiredHours) {

  public RecognitionProgress {
    if (earnedHours < 0 || sessionsCount < 0) {
      throw new IllegalArgumentException("hours and sessions cannot be negative");
    }
    if (requiredHours != null && requiredHours <= 0) {
      throw new IllegalArgumentException("requiredHours must be positive");
    }
  }

  /** Whether the university has opened recognition: without a rule nobody can ask for it. */
  public boolean ruleInForce() {
    return requiredHours != null;
  }

  /** Hours still to teach, zero once the requirement is met; {@code null} without a rule. */
  public Integer missingHours() {
    return requiredHours == null ? null : Math.max(0, requiredHours - earnedHours);
  }

  /** Whether the student reached the hours asked for, which is what lets them request recognition. */
  public boolean requirementMet() {
    return requiredHours != null && earnedHours >= requiredHours;
  }
}
