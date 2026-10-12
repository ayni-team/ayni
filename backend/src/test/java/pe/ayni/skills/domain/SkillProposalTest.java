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
import pe.ayni.skills.domain.model.SkillsStateConflict;

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

  // ---- US43: how a moderator resolves it ----

  private static final UUID MODERATOR = UUID.randomUUID();
  private static final UUID ITEM = UUID.randomUUID();

  @Test
  @DisplayName("approving records who, when, and the catalogue item that was created")
  void approvingRecordsWhoWhenAndTheItemCreated() {
    SkillProposal proposal = propose("Figma", null);

    proposal.approve(MODERATOR, ITEM, "  Widely used  ", NOW.plusSeconds(60));

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.APPROVED);
    assertThat(proposal.isWaiting()).isFalse();
    assertThat(proposal.getResolvedBy()).isEqualTo(MODERATOR);
    assertThat(proposal.getResolvedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(proposal.getCatalogItemId()).isEqualTo(ITEM);
    assertThat(proposal.getDecisionReason()).isEqualTo("Widely used");
  }

  @Test
  @DisplayName("a reason is optional when approving or merging, and a blank one is stored as none")
  void aReasonIsOptionalWhenApprovingOrMerging() {
    SkillProposal approved = propose("Figma", null);
    approved.approve(MODERATOR, ITEM, "   ", NOW);
    SkillProposal merged = propose("Figma", null);
    merged.mergeInto(MODERATOR, ITEM, null, NOW);

    assertThat(approved.getDecisionReason()).isNull();
    assertThat(merged.getDecisionReason()).isNull();
  }

  @Test
  @DisplayName("merging records the existing item, and no new one is implied")
  void mergingRecordsTheExistingItem() {
    SkillProposal proposal = propose("NodeJS", null);

    proposal.mergeInto(MODERATOR, ITEM, "It is Node.js", NOW.plusSeconds(60));

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.MERGED);
    assertThat(proposal.getResolvedBy()).isEqualTo(MODERATOR);
    assertThat(proposal.getResolvedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(proposal.getCatalogItemId()).isEqualTo(ITEM);
    assertThat(proposal.getDecisionReason()).isEqualTo("It is Node.js");
  }

  @Test
  @DisplayName("rejecting records who, when and the reason, and no catalogue item")
  void rejectingRecordsWhoWhenAndTheReason() {
    SkillProposal proposal = propose("Figma", null);

    proposal.reject(MODERATOR, "  Not a tool we teach  ", NOW.plusSeconds(60));

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
    assertThat(proposal.getResolvedBy()).isEqualTo(MODERATOR);
    assertThat(proposal.getResolvedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(proposal.getDecisionReason()).isEqualTo("Not a tool we teach");
    assertThat(proposal.getCatalogItemId()).isNull();
  }

  @Test
  @DisplayName("a rejection without a reason is refused and the proposal keeps waiting")
  void aRejectionWithoutAReasonIsRefused() {
    SkillProposal proposal = propose("Figma", null);

    assertThatThrownBy(() -> proposal.reject(MODERATOR, null, NOW)).isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> proposal.reject(MODERATOR, "   ", NOW)).isInstanceOf(SkillsRuleViolation.class);

    assertThat(proposal.isWaiting()).isTrue();
    assertThat(proposal.getResolvedBy()).isNull();
  }

  @Test
  @DisplayName("a reason longer than the column holds is refused")
  void aReasonLongerThanTheColumnHoldsIsRefused() {
    SkillProposal proposal = propose("Figma", null);

    assertThatThrownBy(() -> proposal.reject(MODERATOR, "x".repeat(501), NOW))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> proposal.approve(MODERATOR, ITEM, "x".repeat(501), NOW))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThat(proposal.isWaiting()).isTrue();
  }

  @Test
  @DisplayName("a proposal is resolved once: any second decision is a conflict and changes nothing")
  void aProposalIsResolvedOnce() {
    SkillProposal proposal = propose("Figma", null);
    proposal.approve(MODERATOR, ITEM, null, NOW);

    assertThatThrownBy(() -> proposal.approve(UUID.randomUUID(), UUID.randomUUID(), null, NOW.plusSeconds(1)))
        .isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> proposal.mergeInto(UUID.randomUUID(), UUID.randomUUID(), null, NOW.plusSeconds(1)))
        .isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> proposal.reject(UUID.randomUUID(), "Changed my mind", NOW.plusSeconds(1)))
        .isInstanceOf(SkillsStateConflict.class);

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.APPROVED);
    assertThat(proposal.getResolvedBy()).isEqualTo(MODERATOR);
    assertThat(proposal.getCatalogItemId()).isEqualTo(ITEM);
    assertThat(proposal.getResolvedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("a rejected or merged proposal cannot be resolved again either")
  void aRejectedOrMergedProposalCannotBeResolvedAgain() {
    SkillProposal rejected = propose("Figma", null);
    rejected.reject(MODERATOR, "No", NOW);
    SkillProposal merged = propose("Figma", null);
    merged.mergeInto(MODERATOR, ITEM, null, NOW);

    assertThatThrownBy(() -> rejected.approve(MODERATOR, ITEM, null, NOW)).isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> merged.reject(MODERATOR, "No", NOW)).isInstanceOf(SkillsStateConflict.class);
  }
}
