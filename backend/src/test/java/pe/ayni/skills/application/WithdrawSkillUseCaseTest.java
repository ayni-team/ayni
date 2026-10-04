package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.NotTheOwner;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/** US18, scenario 5: a tutor stops offering a skill, and the rest of the platform is told. */
class WithdrawSkillUseCaseTest {

  private static final String UPC = "UPC";
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID ANOTHER_TUTOR = UUID.randomUUID();
  private static final UUID CATALOG_ITEM_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final WithdrawSkillUseCase useCase =
      new WithdrawSkillUseCase(offeredSkills, events, Clock.fixed(NOW, ZoneOffset.UTC));

  private OfferedSkill enabledSkillOf(UUID tutor) {
    OfferedSkill skill =
        OfferedSkill.enableByAcademicRecord(
            UUID.randomUUID(),
            UPC,
            tutor,
            CATALOG_ITEM_ID,
            new BigDecimal("15.00"),
            new BigDecimal("13.00"),
            NOW.minusSeconds(3600));
    when(offeredSkills.lockByTenantIdAndId(UPC, skill.getId())).thenReturn(Optional.of(skill));
    return skill;
  }

  private void withdraw(UUID tutor, UUID skillId) {
    TenantContext.runAs(UPC, () -> useCase.execute(tutor, skillId));
  }

  @Test
  @DisplayName("withdrawing an enabled skill takes it out of the offer and says so")
  void withdrawingAnEnabledSkillSaysSo() {
    OfferedSkill skill = enabledSkillOf(TUTOR);

    withdraw(TUTOR, skill.getId());

    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
    assertThat(skill.getUpdatedAt()).isEqualTo(NOW);
    verify(events)
        .publishEvent(
            argThat(
                (Object event) ->
                    event instanceof SkillWithdrawn withdrawn
                        && withdrawn.tenantId().equals(UPC)
                        && withdrawn.tutorId().equals(TUTOR)
                        && withdrawn.catalogItemId().equals(CATALOG_ITEM_ID)
                        && withdrawn.occurredOn().equals(NOW)));
  }

  @Test
  @DisplayName("a skill that does not exist in this university is not found")
  void aSkillThatDoesNotExistIsNotFound() {
    UUID unknown = UUID.randomUUID();
    when(offeredSkills.lockByTenantIdAndId(UPC, unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> withdraw(TUTOR, unknown)).isInstanceOf(NoSuchElementException.class);

    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("another tutor's skill is refused and stays enabled")
  void anotherTutorsSkillIsRefused() {
    OfferedSkill skill = enabledSkillOf(ANOTHER_TUTOR);

    assertThatThrownBy(() -> withdraw(TUTOR, skill.getId())).isInstanceOf(NotTheOwner.class);

    assertThat(skill.isEnabled()).isTrue();
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("a skill already withdrawn is a conflict and announces nothing")
  void aSkillAlreadyWithdrawnIsAConflict() {
    OfferedSkill skill = enabledSkillOf(TUTOR);
    skill.withdraw(NOW.minusSeconds(60));

    assertThatThrownBy(() -> withdraw(TUTOR, skill.getId())).isInstanceOf(SkillsStateConflict.class);

    verify(events, never()).publishEvent(any(Object.class));
  }
}
