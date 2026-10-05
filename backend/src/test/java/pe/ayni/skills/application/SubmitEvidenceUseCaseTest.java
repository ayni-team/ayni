package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.SubmitEvidenceUseCase.EvidenceFileInput;
import pe.ayni.skills.application.SubmitEvidenceUseCase.Submission;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.domain.model.ValidationStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.EvidenceFileRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.ValidationRequestRepository;

/** US16: evidence for a global tool is received whole or not at all. */
class SubmitEvidenceUseCaseTest {

  private static final String UPC = "UPC";
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID TOOL_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final ValidationRequestRepository requests = mock(ValidationRequestRepository.class);
  private final EvidenceFileRepository evidenceFiles = mock(EvidenceFileRepository.class);
  private final EvidenceStoragePort storage = mock(EvidenceStoragePort.class);
  private final EvidenceRules rules = new EvidenceRules(5L * 1024 * 1024, 3);
  private final SubmitEvidenceUseCase useCase =
      new SubmitEvidenceUseCase(
          catalogItems,
          offeredSkills,
          requests,
          evidenceFiles,
          storage,
          rules,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void aToolTheTutorHasNeverSubmitted() {
    when(catalogItems.findByIdAndTenantVisibility(TOOL_ID, UPC)).thenReturn(Optional.of(tool()));
    when(offeredSkills.lockByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, TOOL_ID))
        .thenReturn(Optional.empty());
    when(offeredSkills.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    when(requests.save(any())).thenAnswer(call -> call.getArgument(0));
    when(evidenceFiles.save(any())).thenAnswer(call -> call.getArgument(0));
    when(storage.store(eq(UPC), any())).thenReturn("UPC/key-1", "UPC/key-2", "UPC/key-3");
  }

  private static CatalogItem tool() {
    return new CatalogItem(
        TOOL_ID, CatalogScope.GLOBAL, null, UUID.randomUUID(), "Figma", null, null, NOW);
  }

  private static EvidenceFileInput pdf(String name) {
    byte[] bytes = "%PDF-1.7 the portfolio".getBytes(StandardCharsets.UTF_8);
    return new EvidenceFileInput(name, "application/pdf", bytes.length, new ByteArrayInputStream(bytes));
  }

  private Submission submit(String note, EvidenceFileInput... files) {
    Object[] result = new Object[1];
    TenantContext.runAs(
        UPC, () -> result[0] = useCase.execute(TUTOR, TOOL_ID, note, List.of(files)));
    return (Submission) result[0];
  }

  private static OfferedSkill skillIn(OfferedSkillStatus status) {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, TOOL_ID, NOW);
    switch (status) {
      case PENDING -> {}
      case REJECTED -> skill.rejectEvidence(NOW);
      case ENABLED -> skill.approveByReviewedEvidence(NOW);
      case WITHDRAWN -> {
        skill.approveByReviewedEvidence(NOW);
        skill.withdraw(NOW);
      }
    }
    return skill;
  }

  private void tutorAlreadyHas(OfferedSkill skill) {
    when(offeredSkills.lockByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, TOOL_ID))
        .thenReturn(Optional.of(skill));
  }

  @Test
  @DisplayName("the first submission creates the skill pending and waits for a review")
  void theFirstSubmissionCreatesThePendingSkill() {
    Submission submission = submit("Two years at work", pdf("portfolio.pdf"));

    assertThat(submission.skill().getStatus()).isEqualTo(OfferedSkillStatus.PENDING);
    assertThat(submission.request().getStatus()).isEqualTo(ValidationStatus.SUBMITTED);
    assertThat(submission.request().getStudentNote()).isEqualTo("Two years at work");
    assertThat(submission.request().getOfferedSkillId()).isEqualTo(submission.skill().getId());
    assertThat(submission.files())
        .singleElement()
        .satisfies(
            file -> {
              assertThat(file.getFileName()).isEqualTo("portfolio.pdf");
              assertThat(file.getStorageKey()).isEqualTo("UPC/key-1");
              assertThat(file.getValidationRequestId()).isEqualTo(submission.request().getId());
            });
    verify(offeredSkills).saveAndFlush(any());
    verify(evidenceFiles).flush();
  }

  @Test
  @DisplayName("several files are all stored and all attached to the one request")
  void severalFilesAreAttachedToTheOneRequest() {
    Submission submission = submit(null, pdf("portfolio.pdf"), pdf("certificate.pdf"));

    assertThat(submission.files()).extracting("storageKey").containsExactly("UPC/key-1", "UPC/key-2");
    verify(requests).save(any(ValidationRequest.class));
  }

  @Test
  @DisplayName("after a rejection the same skill goes back to pending with a new request")
  void afterARejectionTheSameSkillGoesBackToPending() {
    OfferedSkill rejected = skillIn(OfferedSkillStatus.REJECTED);
    tutorAlreadyHas(rejected);

    Submission submission = submit("A better certificate", pdf("certificate.pdf"));

    assertThat(submission.skill()).isSameAs(rejected);
    assertThat(rejected.getStatus()).isEqualTo(OfferedSkillStatus.PENDING);
    assertThat(submission.request().getOfferedSkillId()).isEqualTo(rejected.getId());
    verify(offeredSkills, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("evidence that already waits for a review is a conflict and stores nothing")
  void evidenceThatAlreadyWaitsIsAConflict() {
    tutorAlreadyHas(skillIn(OfferedSkillStatus.PENDING));

    assertThatThrownBy(() -> submit(null, pdf("again.pdf"))).isInstanceOf(SkillsStateConflict.class);

    verifyNoInteractions(storage);
    verify(requests, never()).save(any());
  }

  @Test
  @DisplayName("a tool that is already enabled or withdrawn takes no new submission")
  void anEnabledOrWithdrawnToolTakesNoNewSubmission() {
    tutorAlreadyHas(skillIn(OfferedSkillStatus.ENABLED));
    assertThatThrownBy(() -> submit(null, pdf("a.pdf"))).isInstanceOf(SkillsStateConflict.class);

    tutorAlreadyHas(skillIn(OfferedSkillStatus.WITHDRAWN));
    assertThatThrownBy(() -> submit(null, pdf("b.pdf"))).isInstanceOf(SkillsStateConflict.class);

    verifyNoInteractions(storage);
  }

  @Test
  @DisplayName("a course is refused: it is enabled by the academic record")
  void aCourseIsRefused() {
    when(catalogItems.findByIdAndTenantVisibility(TOOL_ID, UPC))
        .thenReturn(
            Optional.of(
                new CatalogItem(
                    TOOL_ID, CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Databases", null, "1ASI0616", NOW)));

    assertThatThrownBy(() -> submit(null, pdf("a.pdf")))
        .isInstanceOf(SkillsRuleViolation.class)
        .hasMessageContaining("academic record");

    verifyNoInteractions(storage, offeredSkills);
  }

  @Test
  @DisplayName("a retired tool is refused")
  void aRetiredToolIsRefused() {
    CatalogItem retired = tool();
    retired.retire();
    when(catalogItems.findByIdAndTenantVisibility(TOOL_ID, UPC)).thenReturn(Optional.of(retired));

    assertThatThrownBy(() -> submit(null, pdf("a.pdf"))).isInstanceOf(SkillsRuleViolation.class);

    verifyNoInteractions(storage, offeredSkills);
  }

  @Test
  @DisplayName("an item that does not exist in this university is not found")
  void anUnknownItemIsNotFound() {
    when(catalogItems.findByIdAndTenantVisibility(TOOL_ID, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> submit(null, pdf("a.pdf"))).isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(storage, offeredSkills);
  }

  @Test
  @DisplayName("files that break the rules are refused before anything is read or stored")
  void filesThatBreakTheRulesAreRefused() {
    assertThatThrownBy(() -> submit(null)).isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(
            () -> submit(null, new EvidenceFileInput("page.html", "text/html", 10, new ByteArrayInputStream(new byte[10]))))
        .isInstanceOf(SkillsRuleViolation.class);

    verifyNoInteractions(storage, offeredSkills);
  }

  @Test
  @DisplayName("a program renamed as a PDF is refused before anything is stored")
  void aProgramRenamedAsAPdfIsRefused() {
    byte[] program = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0};
    EvidenceFileInput fake =
        new EvidenceFileInput("certificate.pdf", "application/pdf", program.length, new ByteArrayInputStream(program));

    assertThatThrownBy(() -> submit(null, fake))
        .isInstanceOf(SkillsRuleViolation.class)
        .hasMessageContaining("certificate.pdf");

    verifyNoInteractions(storage, offeredSkills);
  }

  @Test
  @DisplayName("a note that is too long is refused and stores nothing")
  void aNoteThatIsTooLongIsRefused() {
    assertThatThrownBy(() -> submit("x".repeat(1001), pdf("a.pdf"))).isInstanceOf(SkillsRuleViolation.class);

    verifyNoInteractions(storage);
  }

  @Test
  @DisplayName("two submissions of a new tool at once: the one that loses the race is a conflict")
  void twoSubmissionsAtOnce() {
    when(offeredSkills.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_offered_skills_tutor_item"));

    assertThatThrownBy(() -> submit(null, pdf("a.pdf"))).isInstanceOf(SkillsStateConflict.class);

    verifyNoInteractions(storage);
  }

  @Test
  @DisplayName("when storing the second file fails, the first one is taken back")
  void whenStoringTheSecondFileFailsTheFirstIsTakenBack() {
    when(storage.store(eq(UPC), any()))
        .thenReturn("UPC/key-1")
        .thenThrow(new EvidenceStorageException("disk full", new RuntimeException()));

    assertThatThrownBy(() -> submit(null, pdf("a.pdf"), pdf("b.pdf")))
        .isInstanceOf(EvidenceStorageException.class);

    verify(storage).delete(UPC, "UPC/key-1");
  }

  @Test
  @DisplayName("when the database fails after the files were stored, they are taken back")
  void whenTheDatabaseFailsTheFilesAreTakenBack() {
    doThrow(new RuntimeException("database down")).when(evidenceFiles).flush();

    assertThatThrownBy(() -> submit(null, pdf("a.pdf"), pdf("b.pdf"))).isInstanceOf(RuntimeException.class);

    verify(storage).delete(UPC, "UPC/key-1");
    verify(storage).delete(UPC, "UPC/key-2");
  }

  @Test
  @DisplayName("a failure while taking a file back does not hide the original error")
  void aFailureWhileTakingAFileBackDoesNotHideTheOriginalError() {
    doThrow(new IllegalStateException("database down")).when(evidenceFiles).flush();
    doThrow(new EvidenceStorageException("cannot delete", new RuntimeException()))
        .when(storage)
        .delete(any(), any());

    assertThatThrownBy(() -> submit(null, pdf("a.pdf")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("database down");
  }
}
