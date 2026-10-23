package pe.ayni.audit.application;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.domain.model.ActorRole;
import pe.ayni.identity.IdentityApi;

/**
 * Tells in what role somebody changed the configuration of the current university.
 *
 * <p>The event of a change of identity names who made it and not what they are: a coordinator
 * changes the logo and the colours, and the platform administrator, who belongs to no university,
 * changes the email domains. Identity knows its people of the current university and not the
 * administrators, so a person it does not know is the administrator.
 *
 * <p>The question runs outside the transaction of the listener: identity answers an unknown person
 * with an exception inside a transaction of its own, which would mark the transaction it joined
 * as rollback only even though the exception is expected here.
 */
@Component("auditChangeAuthors")
class ChangeAuthors {

  private final IdentityApi identity;

  ChangeAuthors(IdentityApi identity) {
    this.identity = identity;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  ActorRole roleOf(UUID userId) {
    try {
      identity.requireUser(userId);
      return ActorRole.COORDINATOR;
    } catch (NoSuchElementException notOfTheUniversity) {
      return ActorRole.PLATFORM_ADMIN;
    }
  }
}
