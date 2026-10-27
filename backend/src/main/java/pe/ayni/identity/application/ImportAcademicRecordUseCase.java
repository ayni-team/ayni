package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.AcademicRecord;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.infrastructure.AcademicRecordRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US40 and US41: the student brings their record up to date from their university's academic
 * system (ADR 0006).
 *
 * <p>The approved courses are replaced with what the academic system reports today, and so are the
 * career, the cycle and the current term, which are the university's data and read only in Ayni.
 * The first import moves the onboarding from the academic record to the interests; later ones move
 * nothing.
 */
@Service
public class ImportAcademicRecordUseCase {

    private final UserRepository users;
    private final AcademicRecordRepository academicRecords;
    private final AcademicSystemPort academicSystem;
    private final Clock clock;

    ImportAcademicRecordUseCase(
            UserRepository users,
            AcademicRecordRepository academicRecords,
            AcademicSystemPort academicSystem,
            Clock clock) {

        this.users = users;
        this.academicRecords = academicRecords;
        this.academicSystem = academicSystem;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID userId) {
        String tenantId = TenantContext.require();

        User user =
                users.findByTenantIdAndId(tenantId, userId)
                        .orElseThrow(
                                () ->
                                        new IdentityRuleViolation(
                                                "The user does not belong to this tenant"));

        if (user.getStudentCode() == null
                || user.getStudentCode().isBlank()) {
            throw new IdentityRuleViolation(
                    "The user does not have a student code");
        }

        if (user.getOnboardingStep() == null
                || user.getOnboardingStep() == OnboardingStep.PROFILE) {
            throw new IdentityRuleViolation(
                    "The profile step must be completed before importing the academic record");
        }

        AcademicProfile profile =
                academicSystem.fetchProfile(
                        tenantId,
                        user.getEmail());

        if (profile == null) {
            throw new IdentityRuleViolation(
                    "Academic profile was not found");
        }

        List<AcademicCourseData> courses =
                profile.approvedCourses();

        Instant now = clock.instant();

        academicRecords.deleteByTenantIdAndUserId(
                tenantId,
                userId);

        List<AcademicRecord> imported =
                courses.stream()
                        .map(
                                course ->
                                        new AcademicRecord(
                                                UUID.randomUUID(),
                                                tenantId,
                                                userId,
                                                course.courseCode(),
                                                course.courseName(),
                                                course.grade(),
                                                course.term(),
                                                now))
                        .toList();

        academicRecords.saveAll(imported);

        // Career, cycle and term are the university's and change with the term: they follow the
        // record every time it is imported, as the approved courses do.
        user.refreshAcademicData(
                profile.career(),
                profile.cycle(),
                profile.currentTerm(),
                now);

        user.markAcademicRecordImported(now);
    }
}