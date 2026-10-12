package pe.ayni.audit.domain.model;

/**
 * The patterns that suggest credits are being piled up artificially.
 *
 * <p>Three are detected today. {@code UNUSUAL_FREQUENCY} and {@code NO_BOARD_ACTIVITY} are part of
 * the model but nothing raises them yet: the first needs a baseline of what is usual for a tutor,
 * and the second the whiteboard, which does not exist.
 */
public enum AnomalyKind {
  /** The same tutor and the same student completed more sessions than usual in a period. */
  REPEATED_PAIR,
  UNUSUAL_FREQUENCY,
  /** A completed session lasted a small fraction of what was booked. */
  SHORT_DURATION,
  /** A session ended without every participant confirming they were there. */
  FAILED_PRESENCE,
  NO_BOARD_ACTIVITY
}
