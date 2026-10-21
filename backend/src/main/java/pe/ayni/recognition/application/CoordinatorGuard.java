package pe.ayni.recognition.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.recognition.domain.model.NotACoordinator;

/**
 * Proves that whoever asks is a coordinator of the current university.
 *
 * <p>The route {@code /api/v1/coordinator/**} will one day be closed to everyone else by the access
 * control of the platform. Until then nothing but this check stops a student from reading the queue
 * with the evidence of another student, and afterwards it still belongs here: a role says what kind
 * of person you are, and the use case is where it matters.
 *
 * <p>The bean is named: skills has a guard of its own with the same class name, and two components
 * with the same default name stop the application from starting.
 */
@Component("recognitionCoordinatorGuard")
class CoordinatorGuard {

  private final IdentityApi identity;

  CoordinatorGuard(IdentityApi identity) {
    this.identity = identity;
  }

  /**
   * @return the coordinator, as identity knows them
   * @throws java.util.NoSuchElementException when the person is not a user of the current university
   * @throws NotACoordinator when they are, but not a coordinator
   */
  UserView require(UUID userId) {
    UserView user = identity.requireUser(userId);
    if (user.role() != UserRole.COORDINATOR) {
      throw new NotACoordinator();
    }
    return user;
  }

  /**
   * Like {@link #require}, and the coordinator must also be active: a restricted one can still sign
   * in but cannot change what the university asks of its students.
   *
   * @throws NotACoordinator when they are not a coordinator, or are one who is not active
   */
  UserView requireActive(UUID userId) {
    UserView user = require(userId);
    if (!identity.isActive(userId)) {
      throw new NotACoordinator();
    }
    return user;
  }
}
