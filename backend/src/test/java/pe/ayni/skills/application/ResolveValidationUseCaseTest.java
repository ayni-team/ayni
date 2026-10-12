package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.ValidationResolved;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.application.ResolveValidationUseCase.Resolution;
import pe.ayni.skills.domain.model.AccreditationPath;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.domain.model.ValidationStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.ValidationRequestRepository;

/** US16, scenarios 2 and 3: a coordinator decides, and the rest of the platform is told. */
class ResolveValidationUseCaseTest {

  private static final String UPC = "UPC";
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID TOOL_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-04T16:00:00Z");

  private final IdentityApi identity = mock(IdentityApi.class);
  private final ValidationRequestRepository requests = mock(ValidationRequestRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final ResolveValidationUseCase useCase =
      new ResolveValidationUseCase(
          new CoordinatorGuard(identity),
          requests,
          offeredSkills,
          catalogItems,
          events,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private OfferedSkill skill;
  private ValidationRequest request;

  @BeforeEach
  void aPendingSubmissionAndACoordinator() {
    skill = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, TOOL_ID, NOW.minusSeconds(3600));
    request =
        ValidationRequest.submit(UUID.randomUUID(), UPC, skill.getId(), "My work", NOW.minusSeconds(3600));
    when(identity.requireUser(COORDINATOR)).thenReturn(user(COORDINATOR, UserRole.COORDINATOR));
    when(requests.lockByTenantIdAndId(UPC, request.getId())).thenReturn(Optional.of(request));
    when(offeredSkills.lockByTenantIdAndId(UPC, skill.getId())).thenReturn(Optional.of(skill));
  }

  private static UserView user(UUID id, UserRole role) {
    return new UserView(id, UPC, role, "someone@upc.edu.pe", null, "Someone", null, null, null);
  }

  private Resolution decide(boolean approved, String reason) {
    Object[] result = new Object[1];
    TenantContext.runAs(
        UPC, () -> result[0] = useCase.execute(COORDINATOR, request.getId(), approved, reason));
    return (Resolution) result[0];
  }

  private List<Object> published(int expected) {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(events, times(expected)).publishEvent(captor.capture());
    return captor.getAllValues();
  }

  @Test
  @DisplayName("approving enables the skill by the evidence and records who decided")
  void approvingEnablesTheSkill() {
    decide(true, "Solid portfolio");

    assertThat(request.getStatus()).isEqualTo(ValidationStatus.APPROVED);
    assertThat(request.getReviewedBy()).isEqualTo(COORDINATOR);
    assertThat(request.getReviewedAt()).isEqualTo(NOW);
    assertThat(request.getDecisionReason()).isEqualTo("Solid portfolio");
    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.ENABLED);
    assertThat(skill.getAccreditationPath()).isEqualTo(AccreditationPath.REVIEWED_EVIDENCE);
    assertThat(skill.getEnabledAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("approving tells the student, and tells the search the tool can be found")
  void approvingPublishesBothEvents() {
    decide(true, null);

    List<Object> sent = published(2);
    assertThat(sent)
        .anySatisfy(
            event ->
                assertThat(event)
                    .isEqualTo(
                        new ValidationResolved(UPC, request.getId(), TUTOR, TOOL_ID, true, null, NOW)));
    assertThat(sent).anySatisfy(event -> assertThat(event).isEqualTo(new SkillEnabled(UPC, TUTOR, TOOL_ID, NOW)));
  }

  @Test
  @DisplayName("rejecting refuses the skill and carries the reason to the student")
  void rejectingRefusesTheSkill() {
    decide(false, "The certificate is not legible");

    assertThat(request.getStatus()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(request.getReviewedBy()).isEqualTo(COORDINATOR);
    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.REJECTED);
    assertThat(published(1))
        .singleElement()
        .isEqualTo(
            new ValidationResolved(
                UPC, request.getId(), TUTOR, TOOL_ID, false, "The certificate is not legible", NOW));
  }

  @Test
  @DisplayName("a rejection without a reason is refused and leaves everything as it was")
  void aRejectionWithoutAReasonIsRefused() {
    assertThatThrownBy(() -> decide(false, "  ")).isInstanceOf(SkillsRuleViolation.class);

    assertThat(request.isWaiting()).isTrue();
    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.PENDING);
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("a submission already decided is a conflict and announces nothing again")
  void aSubmissionAlreadyDecidedIsAConflict() {
    decide(true, null);

    assertThatThrownBy(() -> decide(false, "Changed my mind")).isInstanceOf(SkillsStateConflict.class);

    assertThat(request.getStatus()).isEqualTo(ValidationStatus.APPROVED);
    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.ENABLED);
    published(2);
  }

  @Test
  @DisplayName("a student cannot decide, and nothing is read")
  void aStudentCannotDecide() {
    UUID student = UUID.randomUUID();
    when(identity.requireUser(student)).thenReturn(user(student, UserRole.STUDENT));

    assertThatThrownBy(
            () -> TenantContext.runAs(UPC, () -> useCase.execute(student, request.getId(), true, null)))
        .isInstanceOf(NotACoordinator.class);

    verifyNoInteractions(requests, offeredSkills, events);
    assertThat(request.isWaiting()).isTrue();
  }

  @Test
  @DisplayName("someone who is not a user of this university is not found")
  void someoneFromAnotherUniversityIsNotFound() {
    UUID stranger = UUID.randomUUID();
    when(identity.requireUser(stranger)).thenThrow(new NoSuchElementException("no such user"));

    assertThatThrownBy(
            () -> TenantContext.runAs(UPC, () -> useCase.execute(stranger, request.getId(), true, null)))
        .isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(requests, offeredSkills, events);
  }

  @Test
  @DisplayName("a submission that does not exist in this university is not found")
  void aSubmissionThatDoesNotExistIsNotFound() {
    UUID unknown = UUID.randomUUID();
    when(requests.lockByTenantIdAndId(UPC, unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> TenantContext.runAs(UPC, () -> useCase.execute(COORDINATOR, unknown, true, null)))
        .isInstanceOf(NoSuchElementException.class);

    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("evidence for a tool retired while it waited cannot be approved, but can be rejected")
  void evidenceForARetiredToolCannotBeApproved() {
    CatalogItem retired =
        new CatalogItem(TOOL_ID, CatalogScope.GLOBAL, null, UUID.randomUUID(), "Old tool", null, null, NOW);
    retired.retire();
    when(catalogItems.findById(TOOL_ID)).thenReturn(Optional.of(retired));

    assertThatThrownBy(() -> decide(true, "Solid portfolio")).isInstanceOf(SkillsStateConflict.class);
    assertThat(request.isWaiting()).isTrue();
    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.PENDING);
    verify(events, never()).publishEvent(any(Object.class));

    decide(false, "The tool was retired");

    assertThat(request.getStatus()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.REJECTED);
  }
}
