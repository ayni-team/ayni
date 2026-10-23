package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.AcademicRecord;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.AcademicRecordRepository;
import pe.ayni.identity.infrastructure.CreditPolicyRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.tenancy.TenantContext;

class IdentityApiImplTest {

    private final TenantRepository tenants =
            mock(TenantRepository.class);

    private final UserRepository users =
            mock(UserRepository.class);

    private final AcademicRecordRepository records =
            mock(AcademicRecordRepository.class);

    private final CreditPolicyRepository policies =
            mock(CreditPolicyRepository.class);

    private final IdentityApiImpl api =
            new IdentityApiImpl(
                    tenants,
                    users,
                    records,
                    policies,
                    Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void returnsApprovedCoursesOnlyForCurrentTenant() {
        UUID userId = UUID.randomUUID();

        TenantContext.set("UPC");

        when(records.findByTenantIdAndUserId(
                "UPC",
                userId))
                .thenReturn(
                        List.of(
                                new AcademicRecord(
                                        UUID.randomUUID(),
                                        "UPC",
                                        userId,
                                        "SI385",
                                        "Fundamentos de Arquitectura de Software",
                                        BigDecimal.valueOf(18),
                                        "2026-2",
                                        Instant.parse("2026-09-22T14:00:00Z"))));

        List<ApprovedCourseView> result =
                api.approvedCourses(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().courseCode())
                .isEqualTo("SI385");
        assertThat(result.getFirst().grade())
                .isEqualByComparingTo("18");
    }

    @Test
    void findsActiveTenantByInstitutionalEmail() {
        Tenant tenant =
                new Tenant(
                        UUID.randomUUID(),
                        "UPC",
                        "Universidad Peruana de Ciencias Aplicadas",
                        null,
                        null,
                        null,
                        List.of("upc.edu.pe"),
                        BigDecimal.valueOf(17),
                        ZoneId.of("America/Lima"),
                        TenantStatus.ACTIVE,
                        Instant.parse("2026-09-22T14:00:00Z"));

        when(tenants.findByStatus(TenantStatus.ACTIVE))
                .thenReturn(List.of(tenant));

        assertThat(
                api.findTenantByEmailDomain(
                        "student@upc.edu.pe"))
                .isPresent();

        assertThat(
                api.findTenantByEmailDomain(
                                "student@upc.edu.pe")
                        .orElseThrow()
                        .code())
                .isEqualTo("UPC");
    }

    @Test
    void completesTheOnboardingOfAStudentOfTheCurrentTenant() {
        UUID userId = UUID.randomUUID();
        User student = aStudentAt(userId, OnboardingStep.LEARNING_INTERESTS);

        TenantContext.set("UPC");
        when(users.findByTenantIdAndId("UPC", userId))
                .thenReturn(Optional.of(student));

        api.completeOnboarding(userId);

        assertThat(student.getOnboardingStep())
                .isEqualTo(OnboardingStep.COMPLETED);
        assertThat(student.getUpdatedAt())
                .isEqualTo(Instant.parse("2026-10-20T15:00:00Z"));
    }

    @Test
    void refusesToCompleteTheOnboardingBeforeTheAcademicRecordAsAnIllegalState() {
        UUID userId = UUID.randomUUID();

        TenantContext.set("UPC");
        when(users.findByTenantIdAndId("UPC", userId))
                .thenReturn(Optional.of(aStudentAt(userId, OnboardingStep.ACADEMIC_RECORD)));

        // The published interface promises an IllegalStateException, not identity's own class.
        assertThatThrownBy(() -> api.completeOnboarding(userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doesNotFindAUserOfAnotherTenantToCompleteTheOnboarding() {
        UUID userId = UUID.randomUUID();

        TenantContext.set("PUCP");
        when(users.findByTenantIdAndId("PUCP", userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> api.completeOnboarding(userId))
                .isInstanceOf(NoSuchElementException.class);
    }

    private static User aStudentAt(UUID userId, OnboardingStep step) {
        Instant created = Instant.parse("2026-10-01T15:00:00Z");
        return new User(
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
                step,
                created,
                created,
                created);
    }
}