package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.PendingValidationsQuery.PendingValidation;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.EvidenceFile;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.domain.model.ValidationStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.EvidenceFileRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.ValidationRequestRepository;

/** US16: what the coordinator sees in the queue. */
class PendingValidationsQueryTest {

  private static final String UPC = "UPC";
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-04T16:00:00Z");

  private final IdentityApi identity = mock(IdentityApi.class);
  private final ValidationRequestRepository requests = mock(ValidationRequestRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final EvidenceFileRepository evidenceFiles = mock(EvidenceFileRepository.class);
  private final PendingValidationsQuery query =
      new PendingValidationsQuery(
          new CoordinatorGuard(identity), requests, offeredSkills, catalogItems, evidenceFiles, identity);

  @BeforeEach
  void aCoordinatorAsksForTheQueue() {
    when(identity.requireUser(COORDINATOR)).thenReturn(user(COORDINATOR, UserRole.COORDINATOR, "Coordinator"));
  }

  private static UserView user(UUID id, UserRole role, String name) {
    return new UserView(id, UPC, role, "x@upc.edu.pe", "U202310949", name, "Software Engineering", "5", null);
  }

  private static CatalogItem tool(String name) {
    return new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, UUID.randomUUID(), name, null, null, NOW);
  }

  private Page<PendingValidation> page() {
    Object[] result = new Object[1];
    TenantContext.runAs(UPC, () -> result[0] = query.page(COORDINATOR, 0, 20));
    @SuppressWarnings("unchecked")
    Page<PendingValidation> typed = (Page<PendingValidation>) result[0];
    return typed;
  }

  @Test
  @DisplayName("each submission comes with its tool, its tutor and its files")
  void eachSubmissionComesWithItsToolTutorAndFiles() {
    CatalogItem figma = tool("Figma");
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, figma.getId(), NOW);
    ValidationRequest request = ValidationRequest.submit(UUID.randomUUID(), UPC, skill.getId(), "My work", NOW);
    EvidenceFile file =
        new EvidenceFile(UUID.randomUUID(), UPC, request.getId(), "portfolio.pdf", "UPC/k", "application/pdf", 10, NOW);
    when(requests.findByTenantIdAndStatus(eq(UPC), eq(ValidationStatus.SUBMITTED), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
    when(offeredSkills.findAllById(List.of(skill.getId()))).thenReturn(List.of(skill));
    when(catalogItems.findAllById(List.of(figma.getId()))).thenReturn(List.of(figma));
    when(evidenceFiles.findByTenantIdAndValidationRequestIdIn(UPC, List.of(request.getId())))
        .thenReturn(List.of(file));
    when(identity.requireUser(TUTOR)).thenReturn(user(TUTOR, UserRole.STUDENT, "Ana Torres"));

    Page<PendingValidation> result = page();

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent())
        .singleElement()
        .satisfies(
            pending -> {
              assertThat(pending.request()).isSameAs(request);
              assertThat(pending.item()).isSameAs(figma);
              assertThat(pending.tutor().fullName()).isEqualTo("Ana Torres");
              assertThat(pending.files()).containsExactly(file);
            });
  }

  @Test
  @DisplayName("the queue is read oldest first, and only what still waits")
  void theQueueIsReadOldestFirstAndOnlyWhatWaits() {
    when(requests.findByTenantIdAndStatus(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

    page();

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(requests)
        .findByTenantIdAndStatus(eq(UPC), eq(ValidationStatus.SUBMITTED), pageable.capture());
    assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();
  }

  @Test
  @DisplayName("an empty queue reads nothing else")
  void anEmptyQueueReadsNothingElse() {
    when(requests.findByTenantIdAndStatus(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

    assertThat(page().getContent()).isEmpty();

    verifyNoInteractions(offeredSkills, catalogItems, evidenceFiles);
  }

  @Test
  @DisplayName("a tutor with two submissions is asked about once")
  void aTutorWithTwoSubmissionsIsAskedAboutOnce() {
    CatalogItem figma = tool("Figma");
    CatalogItem python = tool("Python");
    OfferedSkill first = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, figma.getId(), NOW);
    OfferedSkill second = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, python.getId(), NOW);
    ValidationRequest firstRequest = ValidationRequest.submit(UUID.randomUUID(), UPC, first.getId(), null, NOW);
    ValidationRequest secondRequest = ValidationRequest.submit(UUID.randomUUID(), UPC, second.getId(), null, NOW);
    when(requests.findByTenantIdAndStatus(any(), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(firstRequest, secondRequest), PageRequest.of(0, 20), 2));
    when(offeredSkills.findAllById(any())).thenReturn(List.of(first, second));
    when(catalogItems.findAllById(any())).thenReturn(List.of(figma, python));
    when(identity.requireUser(TUTOR)).thenReturn(user(TUTOR, UserRole.STUDENT, "Ana Torres"));

    assertThat(page().getContent()).hasSize(2);

    verify(identity, times(1)).requireUser(TUTOR);
  }

  @Test
  @DisplayName("a student cannot read the queue, and nothing is read")
  void aStudentCannotReadTheQueue() {
    UUID student = UUID.randomUUID();
    when(identity.requireUser(student)).thenReturn(user(student, UserRole.STUDENT, "A student"));

    assertThatThrownBy(() -> TenantContext.runAs(UPC, () -> query.page(student, 0, 20)))
        .isInstanceOf(NotACoordinator.class);

    verifyNoInteractions(requests, offeredSkills, catalogItems, evidenceFiles);
  }
}
