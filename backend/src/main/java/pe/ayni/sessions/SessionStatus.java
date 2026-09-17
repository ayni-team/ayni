package pe.ayni.sessions;

/**
 * Lifecycle of a session. UNVERIFIED means it took place but failed the presence check; CANCELLED
 * means its booking was cancelled before it started.
 */
public enum SessionStatus {
  SCHEDULED,
  IN_PROGRESS,
  COMPLETED,
  UNVERIFIED,
  ABANDONED,
  CANCELLED
}
