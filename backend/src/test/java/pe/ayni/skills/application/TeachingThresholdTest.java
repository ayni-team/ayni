package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.skills.domain.model.AcademicSettings;
import pe.ayni.skills.infrastructure.AcademicSettingsRepository;

/** US51: which grade is in force, the one a coordinator set or the one the university registered. */
class TeachingThresholdTest {

  private static final String UPC = "UPC";

  private final IdentityApi identity = mock(IdentityApi.class);
  private final AcademicSettingsRepository settings = mock(AcademicSettingsRepository.class);
  private final TeachingThreshold threshold = new TeachingThreshold(settings, identity);

  @Test
  @DisplayName("the grade a coordinator set wins over the registered one")
  void theGradeSetWins() {
    // A row read from the database: the entity has no way to be built outside it.
    AcademicSettings set = BeanUtils.instantiateClass(AcademicSettings.class);
    ReflectionTestUtils.setField(set, "minimumTeachingGrade", new BigDecimal("16.00"));
    when(settings.findById(UPC)).thenReturn(Optional.of(set));

    assertThat(threshold.of(UPC)).isEqualByComparingTo("16.00");
    verifyNoInteractions(identity);
  }

  @Test
  @DisplayName("without one set, the registered grade is used")
  void withoutOneSetTheRegisteredIsUsed() {
    when(settings.findById(UPC)).thenReturn(Optional.empty());
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", "America/Lima", new BigDecimal("13.00"), true));

    assertThat(threshold.of(UPC)).isEqualByComparingTo("13.00");
  }
}
