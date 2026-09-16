package pe.ayni.sessions;

/** Lifecycle of a session. UNVERIFIED means it took place but failed the presence check. */
public enum SessionStatus {
  SCHEDULED,
  IN_PROGRESS,
  COMPLETED,
  UNVERIFIED,
  ABANDONED
}
