package pe.ayni.sessions.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.SkillsApi;

/**
 * The names a session shows next to the identifiers it keeps: the course, read from skills, and the
 * other person, read from identity.
 *
 * <p>Sessions keeps neither, so both are asked every time, through the published interfaces. An
 * item or a person the other module no longer knows is shown without a name rather than failing
 * the session.
 */
@Component
class SessionParties {

  private final SkillsApi skills;
  private final IdentityApi identity;

  SessionParties(SkillsApi skills, IdentityApi identity) {
    this.skills = skills;
    this.identity = identity;
  }

  SessionCourse courseOf(UUID catalogItemId) {
    CatalogItemView item = skills.itemsOf(Set.of(catalogItemId)).get(catalogItemId);
    return item == null
        ? new SessionCourse(catalogItemId, null, null)
        : new SessionCourse(item.id(), item.name(), item.courseCode());
  }

  /** The participant of the session who is not {@code role}. */
  SessionCounterpart counterpartOf(Session session, ParticipantRole role) {
    UUID counterpartId =
        role == ParticipantRole.STUDENT ? session.getTutorId() : session.getStudentId();
    return new SessionCounterpart(
        counterpartId, identity.namesOf(Set.of(counterpartId)).get(counterpartId));
  }
}
