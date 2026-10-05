package pe.ayni.skills.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.skills.domain.SkillsFixtures.NOW;
import static pe.ayni.skills.domain.SkillsFixtures.TUTOR;
import static pe.ayni.skills.domain.SkillsFixtures.UPC;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.domain.model.SkillsRuleViolation;

/** US42: what a student may propose, and the state in which a proposal is born. */
class SkillProposalTest {

  private static final UUID CATEGORY = UUID.randomUUID();

  private static SkillProposal propose(String name, String description) {
    return SkillProposal.propose(UUID.randomUUID(), UPC, TUTOR, CATEGORY, name, description, NOW);
  }

  @Test
  @DisplayName("a proposal waits for a moderator and has no decision yet")
  void aProposalWaitsForAModerator() {
    SkillProposal proposal = propose("Figma", "Interface design tool");

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
    assertThat(proposal.isWaiting()).isTrue();
    assertThat(proposal.getTenantId()).isEqualTo(UPC);
    assertThat(proposal.getProposedBy()).isEqualTo(TUTOR);
    assertThat(proposal.getCategoryId()).isEqualTo(CATEGORY);
    assertThat(proposal.getResolvedBy()).isNull();
    assertThat(proposal.getResolvedAt()).isNull();
    assertThat(proposal.getDecisionReason()).isNull();
    assertThat(proposal.getCatalogItemId()).isNull();
    assertThat(proposal.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("the name is stripped")
  void theNameIsStripped() {
    assertThat(propose("  Figma  ", null).getName()).isEqualTo("Figma");
  }

  @Test
  @DisplayName("a blank description is stored as no description, and a description is stripped")
  void aBlankDescriptionIsStoredAsNone() {
    assertThat(propose("Figma", "   ").getDescription()).isNull();
    assertThat(propose("Figma", null).getDescription()).isNull();
    assertThat(propose("Figma", "  design  ").getDescription()).isEqualTo("design");
  }

  @Test
  @DisplayName("a name that is missing, blank or shorter than three characters is refused")
  void aNameThatIsMissingOrTooShortIsRefused() {
    assertThatThrownBy(() -> propose(null, null)).isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> propose("   ", null)).isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> propose(" ab ", null)).isInstanceOf(SkillsRuleViolation.class);
    assertThat(propose("Git", null).getName()).isEqualTo("Git");
  }

  @Test
  @DisplayName("a name longer than the column holds is refused")
  void aNameLongerThanTheColumnHoldsIsRefused() {
    assertThatThrownBy(() -> propose("x".repeat(161), null)).isInstanceOf(SkillsRuleViolation.class);
    assertThat(propose("x".repeat(160), null).getName()).hasSize(160);
  }

  @Test
  @DisplayName("a description longer than the column holds is refused")
  void aDescriptionLongerThanTheColumnHoldsIsRefused() {
    assertThatThrownBy(() -> propose("Figma", "x".repeat(501)))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThat(propose("Figma", "x".repeat(500)).getDescription()).hasSize(500);
  }
}
