package pe.ayni.skills.application;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.skills.domain.model.NotACoordinator;

/**
 * Proves that whoever asks is a coordinator of the current university.
 *
 * <p>The route {@code /api/v1/coordinator/**} will one day be closed to everyone else by the access
 * control of the platform. Until then nothing but this check stops a student from reading the
 * queue, and afterwards it still belongs here: a role says what kind of person you are, and the use
 * case is where it matters.
 */
@Component
class CoordinatorGuard {

  private final IdentityApi identity;

  CoordinatorGuard(IdentityApi identity) {
    this.identity = identity;
  }

  /**
   * @throws NoSuchElementException when the person is not a user of the current university
   * @throws NotACoordinator when they are, but not a coordinator
   */
  void require(UUID userId) {
    UserView user = identity.requireUser(userId);
    if (user.role() != UserRole.COORDINATOR) {
      throw new NotACoordinator();
    }
  }
}
