package pe.ayni.analytics.application;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.analytics.domain.model.IndicatorsAccessDenied;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Proves that whoever asks is an active coordinator of the university of the request.
 *
 * <p>The bean is named: audit, skills and recognition have a guard of their own with the same class
 * name, and two components with the same default name stop the application from starting.
 */
@Component("analyticsCoordinatorGuard")
class CoordinatorGuard {

  private final IdentityApi identity;

  CoordinatorGuard(IdentityApi identity) {
    this.identity = identity;
  }

  /**
   * @throws IndicatorsAccessDenied when the person is unknown to the university, is not a
   *     coordinator of it, or is one who is not active
   */
  void require(UUID userId) {
    UserView user;
    try {
      user = identity.requireUser(userId);
    } catch (NoSuchElementException unknown) {
      throw new IndicatorsAccessDenied();
    }
    if (user == null
        || user.role() != UserRole.COORDINATOR
        || !TenantContext.require().equals(user.tenantId())
        || !identity.isActive(userId)) {
      throw new IndicatorsAccessDenied();
    }
  }
}
