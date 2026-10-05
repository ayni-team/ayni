package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.RetireCatalogItemUseCase.Retirement;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/** US51, scenario 4: retiring a course reaches only the courses of the university. */
class RetireCourseUseCaseTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final CoordinatorGuard guard = new CoordinatorGuard(identity);
  private final RetireCourseUseCase useCase =
      new RetireCourseUseCase(
          guard,
          catalogItems,
          new RetireCatalogItemUseCase(
              guard, catalogItems, offeredSkills, events, Clock.fixed(NOW, ZoneOffset.UTC)));

  private CatalogItem course;

  @BeforeEach
  void aCourseAndACoordinator() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
    course =
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Databases", null, "1ASI0616", NOW);
    when(catalogItems.findByIdAndTenantVisibility(course.getId(), UPC)).thenReturn(Optional.of(course));
    when(catalogItems.lockByIdAndTenantVisibility(course.getId(), UPC)).thenReturn(Optional.of(course));
    when(offeredSkills.lockByCatalogItemIdAndStatus(course.getId(), OfferedSkillStatus.ENABLED))
        .thenReturn(List.of());
  }

  private Retirement retire(UUID asking, UUID courseId, long confirmed) {
    AtomicReference<Retirement> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(useCase.execute(asking, courseId, confirmed)));
    return result.get();
  }

  @Test
  @DisplayName("a course of the university is retired and its tutors lose the offer")
  void aCourseIsRetired() {
    OfferedSkill offer = OfferedSkill.enableByAcademicRecord(
        UUID.randomUUID(), UPC, UUID.randomUUID(), course.getId(), new java.math.BigDecimal("15.00"),
        new java.math.BigDecimal("13.00"), NOW);
    when(offeredSkills.lockByCatalogItemIdAndStatus(course.getId(), OfferedSkillStatus.ENABLED))
        .thenReturn(List.of(offer));

    Retirement retirement = retire(COORDINATOR, course.getId(), 1);

    assertThat(retirement.tutorsAffected()).isEqualTo(1);
    assertThat(course.isActive()).isFalse();
    assertThat(offer.getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
    verify(events).publishEvent(any(SkillWithdrawn.class));
  }

  @Test
  @DisplayName("the confirmation still has to match the tutors affected")
  void theConfirmationStillHasToMatch() {
    assertThatThrownBy(() -> retire(COORDINATOR, course.getId(), 5)).isInstanceOf(SkillsStateConflict.class);

    assertThat(course.isActive()).isTrue();
  }

  @Test
  @DisplayName("a global tool is not found here, and is not retired")
  void aGlobalToolIsNotFound() {
    CatalogItem tool =
        new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, UUID.randomUUID(), "Figma", null, null, NOW);
    when(catalogItems.findByIdAndTenantVisibility(tool.getId(), UPC)).thenReturn(Optional.of(tool));

    assertThatThrownBy(() -> retire(COORDINATOR, tool.getId(), 0)).isInstanceOf(NoSuchElementException.class);

    assertThat(tool.isActive()).isTrue();
    verify(catalogItems, never()).lockByIdAndTenantVisibility(any(), any());
  }

  @Test
  @DisplayName("a course of another university is not found")
  void aCourseOfAnotherUniversityIsNotFound() {
    UUID elsewhere = UUID.randomUUID();
    when(catalogItems.findByIdAndTenantVisibility(elsewhere, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> retire(COORDINATOR, elsewhere, 0)).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("a student cannot retire a course")
  void aStudentCannotRetire() {
    assertThatThrownBy(() -> retire(STUDENT, course.getId(), 0)).isInstanceOf(NotACoordinator.class);

    assertThat(course.isActive()).isTrue();
  }
}
