package pe.ayni.audit.domain.model;

/**
 * Somebody who is not an active coordinator of the university asked for its activity log.
 *
 * <p>It carries who they are for the university, so the attempt can be written down as one more
 * entry before the refusal is answered.
 */
public class AuditAccessDenied extends RuntimeException {

  private final transient ActorRole role;
  private final String reason;

  public AuditAccessDenied(ActorRole role, String reason) {
    super("Only an active coordinator of the university can read its activity log");
    this.role = role;
    this.reason = reason;
  }

  /** Who the person is in the university, {@code UNKNOWN} when it does not know them. */
  public ActorRole role() {
    return role;
  }

  /** Why they were refused: UNKNOWN_USER, NOT_A_COORDINATOR or NOT_ACTIVE. */
  public String reason() {
    return reason;
  }
}
