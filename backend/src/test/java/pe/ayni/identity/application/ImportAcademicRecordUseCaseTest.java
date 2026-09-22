package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.AcademicRecord;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.AcademicRecordRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.OnboardingStep;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.OnboardingStep;
class ImportAcademicRecordUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-09-22T14:00:00Z");
    @Test
    void rejectsAcademicImportBeforeProfileStep() {
        UserRepository users =
                mock(UserRepository.class);

        AcademicRecordRepository records =
                mock(AcademicRecordRepository.class);

        AcademicSystemPort academicSystem =
                mock(AcademicSystemPort.class);

        UUID userId = UUID.randomUUID();

        User user =
                new User(
                        userId,
                        "UPC",
                        UserRole.STUDENT,
                        "u202612345@upc.edu.pe",
                        "U202612345",
                        "Juan Sanchez",
                        "Software Engineering",
                        "7",
                        null,
                        null,
                        UserStatus.ACTIVE,
                        OnboardingStep.PROFILE,
                        NOW,
                        NOW,
                        NOW);

        when(users.findByTenantIdAndId("UPC", userId))
                .thenReturn(Optional.of(user));

        ImportAcademicRecordUseCase useCase =
                new ImportAcademicRecordUseCase(
                        users,
                        records,
                        academicSystem,
                        Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(
                () -> useCase.execute("UPC", userId))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining("profile step");
    }
    @Test
    void importsAcademicRecordsForTenantUser() {
        UserRepository users =
                mock(UserRepository.class);

        AcademicRecordRepository records =
                mock(AcademicRecordRepository.class);

        AcademicSystemPort academicSystem =
                mock(AcademicSystemPort.class);

        UUID userId = UUID.randomUUID();

        User user =
                new User(
                        userId,
                        "UPC",
                        UserRole.STUDENT,
                        "u202612345@upc.edu.pe",
                        "U202612345",
                        "Juan Sanchez",
                        "Software Engineering",
                        "7",
                        null,
                        null,
                        UserStatus.ACTIVE,
                        OnboardingStep.ACADEMIC_RECORD,
                        NOW,
                        NOW,
                        NOW);

        when(users.findByTenantIdAndId("UPC", userId))
                .thenReturn(Optional.of(user));

        when(academicSystem.findApprovedCourses(
                "UPC",
                "U202612345"))
                .thenReturn(
                        List.of(
                                new AcademicCourseData(
                                        "SI385",
                                        "Fundamentos de Arquitectura de Software",
                                        BigDecimal.valueOf(18),
                                        "2026-2")));

        ImportAcademicRecordUseCase useCase =
                new ImportAcademicRecordUseCase(
                        users,
                        records,
                        academicSystem,
                        Clock.fixed(NOW, ZoneOffset.UTC));

        useCase.execute("UPC", userId);

        verify(records)
                .deleteByTenantIdAndUserId("UPC", userId);

        ArgumentCaptor<List<AcademicRecord>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(records).saveAll(captor.capture());

        assertThat(captor.getValue()).hasSize(1);

        AcademicRecord imported =
                captor.getValue().getFirst();

        assertThat(imported.getTenantId())
                .isEqualTo("UPC");

        assertThat(imported.getUserId())
                .isEqualTo(userId);

        assertThat(imported.getCourseCode())
                .isEqualTo("SI385");

        assertThat(imported.getGrade())
                .isEqualByComparingTo("18");

        assertThat(imported.getSyncedAt())
                .isEqualTo(NOW);
        assertThat(user.getOnboardingStep())
                .isEqualTo(OnboardingStep.LEARNING_INTERESTS);
    }
}