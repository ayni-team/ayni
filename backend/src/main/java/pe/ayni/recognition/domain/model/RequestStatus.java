package pe.ayni.recognition.domain.model;

/** Where a request stands. A request ends approved or rejected, and does not move after that. */
public enum RequestStatus {
  /** Sent by the student, nobody has looked at it. */
  SUBMITTED,
  /** A coordinator is evaluating it. */
  UNDER_REVIEW,
  APPROVED,
  REJECTED;

  public boolean isResolved() {
    return this == APPROVED || this == REJECTED;
  }
}
