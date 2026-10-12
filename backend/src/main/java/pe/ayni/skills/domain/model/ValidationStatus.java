package pe.ayni.skills.domain.model;

/** Where a submission of evidence is in its review. */
public enum ValidationStatus {

  /** Waiting in the coordinator's queue. */
  SUBMITTED,

  /** A coordinator accepted the evidence. */
  APPROVED,

  /** A coordinator refused the evidence, with a reason the student can read. */
  REJECTED
}
