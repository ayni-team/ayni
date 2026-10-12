package pe.ayni.audit.domain.services;

import pe.ayni.audit.domain.model.Severity;

/**
 * When the same tutor and the same student completed more sessions together than is usual.
 *
 * <p>Usual is up to the threshold, which is a number of sessions in the period the caller looks at.
 * The rule does not know the period: it only judges a count.
 */
public final class RepeatedPairRule {

  private final int threshold;

  /** @param threshold how many sessions of one pair are usual in a period, at least 1 */
  public RepeatedPairRule(int threshold) {
    if (threshold < 1) {
      throw new IllegalArgumentException("repeated-pair-threshold must be at least 1");
    }
    this.threshold = threshold;
  }

  public int threshold() {
    return threshold;
  }

  /** Whether the pair completed more sessions than is usual. */
  public boolean exceeds(long sessions) {
    return sessions > threshold;
  }

  /** How much the finding weighs: serious when it doubles what is usual. */
  public Severity severityOf(long sessions) {
    return sessions >= 2L * threshold ? Severity.HIGH : Severity.MEDIUM;
  }
}
