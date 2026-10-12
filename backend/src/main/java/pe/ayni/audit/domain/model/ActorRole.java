package pe.ayni.audit.domain.model;

/**
 * Who acted, as an entry of the log states it.
 *
 * <p>{@code SYSTEM} is for what nobody asked for: a job closing a session, a grade enabling a
 * course. {@code UNKNOWN} is for somebody who claimed to be a person the university does not know,
 * which only a refused attempt to read the log can carry.
 */
public enum ActorRole {
  STUDENT,
  TUTOR,
  COORDINATOR,
  SYSTEM,
  UNKNOWN
}
