package pe.ayni.skills.domain.model;

/** How an offered skill reached {@link OfferedSkillStatus#ENABLED}. */
public enum AccreditationPath {

  /** The academic system reports a grade that clears the university's threshold. */
  ACADEMIC_RECORD,

  /** A moderator reviewed evidence the student submitted, because no academic record applies. */
  REVIEWED_EVIDENCE
}
