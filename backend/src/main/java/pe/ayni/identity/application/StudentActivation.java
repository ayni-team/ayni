package pe.ayni.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.AcademicRecord;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.infrastructure.AcademicRecordRepository;
import pe.ayni.identity.infrastructure.UserRepository;

/**
 * Activating a student from their university's academic system (US38, ADR 0006), in two halves.
 *
 * <p>{@link #admit} asks the academic system and decides whether the person may be a student of
 * Ayni at all; it writes nothing, so a refusal leaves an access link unused. {@link #create} writes
 * the student and their approved courses. {@link ConfirmAccessUseCase} consumes the link between
 * the two and publishes {@code StudentActivated} after them. The demonstration data, which has no
 * link to open, calls the same two halves with an identifier of its own.
 */
@Component
public class StudentActivation {

    static final String ACCOUNT_ALREADY_EXISTS =
            "An account already exists for this email. Ask for a new sign in link";

    static final String NOT_ENROLLED =
            "The student is not currently enrolled at the university";

    private final UserRepository users;
    private final AcademicRecordRepository academicRecords;
    private final AcademicSystemPort academicSystem;

    StudentActivation(
            UserRepository users,
            AcademicRecordRepository academicRecords,
            AcademicSystemPort academicSystem) {

        this.users = users;
        this.academicRecords = academicRecords;
        this.academicSystem = academicSystem;
    }

    /**
     * What the academic system says of the person with that institutional email, when they may be
     * activated.
     *
     * @throws IdentityRuleViolation when the address already has an account, the university has no
     *     academic system, the academic system does not know the address, or the university no
     *     longer considers them its student (a graduate, somebody who left)
     */
    public AcademicProfile admit(
            String tenantId,
            String email) {

        if (users.existsByTenantIdAndEmailIgnoreCase(
                tenantId,
                email)) {

            throw new IdentityRuleViolation(
                    ACCOUNT_ALREADY_EXISTS);
        }

        /*
         * The academic system finds the student by their institutional
         * email and answers the code the university assigned them (ADR
         * 0006): Ayni neither derives it nor checks its format.
         */
        AcademicProfile profile =
                academicSystem.fetchProfile(
                        tenantId,
                        email);

        if (!profile.isEnrolled()) {
            throw new IdentityRuleViolation(
                    NOT_ENROLLED);
        }

        return profile;
    }

    /**
     * Writes the active student of the profile and their approved courses, flushed, so a listener
     * of the activation that asks identity right away finds both.
     */
    @Transactional
    public User create(
            String tenantId,
            UUID userId,
            String email,
            AcademicProfile profile,
            Instant now) {

        User user =
                User.activatedStudent(
                        userId,
                        tenantId,
                        email,
                        profile.studentCode(),
                        profile.fullName(),
                        profile.career(),
                        profile.cycle(),
                        profile.currentTerm(),
                        now);

        users.saveAndFlush(user);

        List<AcademicRecord> records =
                profile.approvedCourses()
                        .stream()
                        .map(
                                course ->
                                        new AcademicRecord(
                                                UUID.randomUUID(),
                                                tenantId,
                                                user.getId(),
                                                course.courseCode(),
                                                course.courseName(),
                                                course.grade(),
                                                course.term(),
                                                now))
                        .toList();

        academicRecords.saveAllAndFlush(records);

        return user;
    }
}
