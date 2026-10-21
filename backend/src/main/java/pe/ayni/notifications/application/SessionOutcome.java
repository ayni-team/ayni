package pe.ayni.notifications.application;

/** How a session ended, as the notice of its closing records it. */
enum SessionOutcome {
  /** Both participants proved their presence. */
  COMPLETED,

  /** At least one did not, so nobody earned anything and the student was refunded. */
  UNVERIFIED
}
