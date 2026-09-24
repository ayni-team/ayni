package pe.ayni.identity.application;

import pe.ayni.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.AcademicRecord;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.infrastructure.AcademicRecordRepository;
import pe.ayni.identity.infrastructure.UserRepository;

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

        List<AcademicCourseData> courses =
                academicSystem.findApprovedCourses(
                        tenantId,
                        user.getStudentCode());

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
        user.markAcademicRecordImported(now);
    }
}