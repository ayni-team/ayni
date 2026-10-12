package pe.ayni.skills.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.skills.domain.SkillsFixtures.NOW;
import static pe.ayni.skills.domain.SkillsFixtures.UPC;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.domain.model.ValidationStatus;

/** What a coordinator may do with a submission of evidence, and what the student is owed. */
class ValidationRequestTest {

  private static final UUID COORDINATOR = UUID.randomUUID();

  private static ValidationRequest submitted(String note) {
    return ValidationRequest.submit(UUID.randomUUID(), UPC, UUID.randomUUID(), note, NOW);
  }

  @Test
  @DisplayName("a submission waits for a review and has no reviewer yet")
  void aSubmissionWaitsForAReview() {
    ValidationRequest request = submitted("My portfolio is on the link");

    assertThat(request.getStatus()).isEqualTo(ValidationStatus.SUBMITTED);
    assertThat(request.isWaiting()).isTrue();
    assertThat(request.getReviewedBy()).isNull();
    assertThat(request.getReviewedAt()).isNull();
    assertThat(request.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("a blank note is stored as no note, and a note is stripped")
  void aBlankNoteIsStoredAsNoNote() {
    assertThat(submitted("   ").getStudentNote()).isNull();
    assertThat(submitted(null).getStudentNote()).isNull();
    assertThat(submitted("  two years of work  ").getStudentNote()).isEqualTo("two years of work");
  }

  @Test
  @DisplayName("a note longer than the column holds is refused")
  void aNoteLongerThanTheColumnHoldsIsRefused() {
    assertThatThrownBy(() -> submitted("x".repeat(1001))).isInstanceOf(SkillsRuleViolation.class);
    assertThat(submitted("x".repeat(1000)).getStudentNote()).hasSize(1000);
  }

  @Test
  @DisplayName("approving records who decided and when, and the reason is optional")
  void approvingRecordsWhoDecidedAndWhen() {
    ValidationRequest request = submitted(null);

    request.approve(COORDINATOR, "  ", NOW.plusSeconds(60));

    assertThat(request.getStatus()).isEqualTo(ValidationStatus.APPROVED);
    assertThat(request.isWaiting()).isFalse();
    assertThat(request.getReviewedBy()).isEqualTo(COORDINATOR);
    assertThat(request.getReviewedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(request.getDecisionReason()).isNull();
  }

  @Test
  @DisplayName("rejecting records the reason the student will read")
  void rejectingRecordsTheReason() {
    ValidationRequest request = submitted(null);

    request.reject(COORDINATOR, " The certificate is not legible ", NOW.plusSeconds(60));

    assertThat(request.getStatus()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(request.getReviewedBy()).isEqualTo(COORDINATOR);
    assertThat(request.getDecisionReason()).isEqualTo("The certificate is not legible");
  }

  @Test
  @DisplayName("a rejection without a reason is refused and the request keeps waiting")
  void aRejectionWithoutAReasonIsRefused() {
    ValidationRequest request = submitted(null);

    assertThatThrownBy(() -> request.reject(COORDINATOR, "  ", NOW.plusSeconds(60)))
        .isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> request.reject(COORDINATOR, null, NOW.plusSeconds(60)))
        .isInstanceOf(SkillsRuleViolation.class);

    assertThat(request.isWaiting()).isTrue();
    assertThat(request.getReviewedBy()).isNull();
  }

  @Test
  @DisplayName("a reason longer than the column holds is refused")
  void aReasonLongerThanTheColumnHoldsIsRefused() {
    ValidationRequest request = submitted(null);

    assertThatThrownBy(() -> request.reject(COORDINATOR, "x".repeat(501), NOW))
        .isInstanceOf(SkillsRuleViolation.class);

    assertThat(request.isWaiting()).isTrue();
  }

  @Test
  @DisplayName("a request decided once cannot be decided again, either way")
  void aRequestDecidedOnceCannotBeDecidedAgain() {
    ValidationRequest approved = submitted(null);
    approved.approve(COORDINATOR, null, NOW);
    ValidationRequest rejected = submitted(null);
    rejected.reject(COORDINATOR, "No evidence of the work", NOW);

    assertThatThrownBy(() -> approved.reject(COORDINATOR, "Changed my mind", NOW))
        .isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> rejected.approve(COORDINATOR, null, NOW))
        .isInstanceOf(SkillsStateConflict.class);

    assertThat(approved.getStatus()).isEqualTo(ValidationStatus.APPROVED);
    assertThat(rejected.getStatus()).isEqualTo(ValidationStatus.REJECTED);
  }
}
