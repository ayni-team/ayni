package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.application.MinimumGradeQuery.MinimumGrade;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.infrastructure.AcademicSettingsRepository;

/** US51, scenarios 2 and 3: who may set the minimum grade, which values it takes, and what is read back. */
class SetMinimumGradeUseCaseTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final AcademicSettingsRepository settings = mock(AcademicSettingsRepository.class);
  private final CoordinatorGuard guard = new CoordinatorGuard(identity);
  private final SetMinimumGradeUseCase useCase =
      new SetMinimumGradeUseCase(guard, settings, Clock.fixed(NOW, ZoneOffset.UTC));
  private final MinimumGradeQuery query =
      new MinimumGradeQuery(guard, new TeachingThreshold(settings, identity), settings);

  @BeforeEach
  void aCoordinatorAndAStudent() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", "America/Lima", new BigDecimal("13.00"), true));
    when(settings.findById(UPC)).thenReturn(Optional.empty());
  }

  private MinimumGrade set(UUID asking, String grade) {
    AtomicReference<MinimumGrade> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(useCase.execute(asking, new BigDecimal(grade))));
    return result.get();
  }

  private MinimumGrade read(UUID asking) {
    AtomicReference<MinimumGrade> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.current(asking)));
    return result.get();
  }

  @Test
  @DisplayName("setting the grade saves it for the university with two decimals and says when")
  void settingTheGradeSavesIt() {
    MinimumGrade saved = set(COORDINATOR, "14");

    assertThat(saved.grade()).isEqualByComparingTo("14.00");
    assertThat(saved.grade().scale()).isEqualTo(2);
    assertThat(saved.updatedAt()).isEqualTo(NOW);
    verify(settings).saveMinimumGrade(UPC, new BigDecimal("14.00"), COORDINATOR, NOW);
  }

  @Test
  @DisplayName("the limits of the scale are accepted: zero and twenty")
  void theLimitsOfTheScaleAreAccepted() {
    assertThat(set(COORDINATOR, "0").grade()).isEqualByComparingTo("0");
    assertThat(set(COORDINATOR, "20.00").grade()).isEqualByComparingTo("20");
  }

  @Test
  @DisplayName("a grade below zero, above twenty or with three decimals is refused and nothing is saved")
  void anInvalidGradeIsRefused() {
    for (String grade : new String[] {"-0.01", "20.01", "99", "13.555"}) {
      assertThatThrownBy(() -> set(COORDINATOR, grade)).isInstanceOf(SkillsRuleViolation.class);
    }
    verifyNoInteractions(settings);
  }

  @Test
  @DisplayName("trailing zeros past two decimals are not a third decimal")
  void trailingZerosAreNotADecimal() {
    assertThat(set(COORDINATOR, "13.500").grade()).isEqualByComparingTo("13.50");
  }

  @Test
  @DisplayName("a student cannot set it, and nothing is saved")
  void aStudentCannotSetIt() {
    assertThatThrownBy(() -> set(STUDENT, "10")).isInstanceOf(NotACoordinator.class);

    verify(settings, never()).saveMinimumGrade(any(), any(), any(), any());
  }

  @Test
  @DisplayName("until a coordinator sets one, the grade is the one the university was registered with")
  void untilSetTheGradeIsTheRegisteredOne() {
    MinimumGrade current = read(COORDINATOR);

    assertThat(current.grade()).isEqualByComparingTo("13.00");
    assertThat(current.updatedAt()).isNull();
  }

  @Test
  @DisplayName("a student cannot read it")
  void aStudentCannotReadIt() {
    assertThatThrownBy(() -> read(STUDENT)).isInstanceOf(NotACoordinator.class);
  }
}
