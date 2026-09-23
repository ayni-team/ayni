package pe.ayni.skills.domain.model;

/** Where an offered skill is in its life. */
public enum OfferedSkillStatus {

  /** Submitted, waiting on a path that resolves asynchronously (reviewed evidence). */
  PENDING,

  /** The tutor may receive reservations for it. */
  ENABLED,

  /** The accreditation was refused. */
  REJECTED,

  /** The tutor stopped offering it. */
  WITHDRAWN
}
