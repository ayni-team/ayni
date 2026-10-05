package pe.ayni.skills.domain.model;

/** Where a proposed tool is in its resolution. */
public enum ProposalStatus {

  /** Waiting for a moderator. */
  PROPOSED,

  /** A moderator added the tool to the catalogue. */
  APPROVED,

  /** A moderator refused it, with a reason the student can read. */
  REJECTED
}
