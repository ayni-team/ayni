package pe.ayni.audit.domain.model;

/**
 * The patterns that suggest credits are being piled up artificially.
 *
 * <p>Four are detected today. {@code UNUSUAL_FREQUENCY} is part of the model but nothing raises it
 * yet: it needs a baseline of what is usual for a tutor.
 */
public enum AnomalyKind {
  /** The same tutor and the same student completed more sessions than usual in a period. */
  REPEATED_PAIR,
  UNUSUAL_FREQUENCY,
  /** A completed session lasted a small fraction of what was booked. */
  SHORT_DURATION,
  /** A session ended without every participant confirming they were there. */
  FAILED_PRESENCE,
  /** A completed session ended with nothing drawn on its whiteboard. */
  NO_BOARD_ACTIVITY
}
