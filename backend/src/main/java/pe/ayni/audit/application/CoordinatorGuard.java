package pe.ayni.audit.application;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.audit.domain.model.ActorRole;
import pe.ayni.audit.domain.model.AuditAccessDenied;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;

/**
 * Proves that whoever asks is an active coordinator of the current university.
 *
 * <p>The bean is named: skills and recognition have a guard of their own with the same class name,
 * and two components with the same default name stop the application from starting.
 */
@Component("auditCoordinatorGuard")
class CoordinatorGuard {

  private final IdentityApi identity;

  CoordinatorGuard(IdentityApi identity) {
    this.identity = identity;
  }

  /**
   * @throws AuditAccessDenied when the person is not known to the university, is not a coordinator,
   *     or is one who is not active; it says who they are, so the attempt can be recorded
   */
  void require(UUID userId) {
    UserView user;
    try {
      user = identity.requireUser(userId);
    } catch (NoSuchElementException unknown) {
      throw new AuditAccessDenied(ActorRole.UNKNOWN, "UNKNOWN_USER");
    }
    if (user.role() != UserRole.COORDINATOR) {
      throw new AuditAccessDenied(ActorRole.STUDENT, "NOT_A_COORDINATOR");
    }
    if (!identity.isActive(userId)) {
      throw new AuditAccessDenied(ActorRole.COORDINATOR, "NOT_ACTIVE");
    }
  }
}
