package pe.ayni.skills.domain.model;

/** Where a proposed tool is in its resolution. */
public enum ProposalStatus {

  /** Waiting for a moderator. */
  PROPOSED,

  /** A moderator added the tool to the catalogue as a new item. */
  APPROVED,

  /** A moderator found that the catalogue already had the tool and joined the proposal to it. */
  MERGED,

  /** A moderator refused it, with a reason the student can read. */
  REJECTED
}
