package pe.ayni.skills.application;

import pe.ayni.skills.domain.model.SkillProposal;

/**
 * A proposal next to the name of its category, which is what the student reads.
 *
 * @param categoryName the name the category has today
 */
public record ProposalView(SkillProposal proposal, String categoryName) {}
