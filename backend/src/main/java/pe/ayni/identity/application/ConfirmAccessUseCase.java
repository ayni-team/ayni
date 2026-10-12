package pe.ayni.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.AcademicRecord;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.AcademicRecordRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.StudentActivated;

/**
 * US38: opening the link received by email either signs an existing student in
 * or activates a new student using the university academic system.
 *
 * <p>The token identifies the access link. The tenant is read from that link,
 * never supplied by the client.
 *
 * <p>An activation obtains the student code from the local part of the
 * institutional email and uses the academic-system boundary to obtain the
 * student's name, career, current term and approved courses.
 *
 * <p>The link is consumed with a conditional database update before account
 * or session data is written. Therefore the same link cannot open two sessions
 * or activate the same student twice.
 */
@Service
public class ConfirmAccessUseCase {

    static final String INVALID =
            "This access link is not valid. Ask for a new one";

    static final String EXPIRED_OR_USED =
            "This access link has expired or was already used. Ask for a new one";

    static final String INVITATION_PENDING =
            "This link accepts a coordinator invitation, which is not available yet";

    static final String NO_UNIVERSITY =
            "Signing in without a university, as a platform administrator, is not available yet";

    static final String NOT_ACTIVE =
            "This account is not active";

    static final String ACCOUNT_ALREADY_EXISTS =
            "An account already exists for this email. Ask for a new sign in link";

    private final AccessLinkRepository accessLinks;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final AcademicRecordRepository academicRecords;
    private final AcademicSystemPort academicSystem;
    private final AccessTokenGenerator tokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration sessionTtl;

    ConfirmAccessUseCase(
            AccessLinkRepository accessLinks,
            UserRepository users,
            UserSessionRepository sessions,
            AcademicRecordRepository academicRecords,
            AcademicSystemPort academicSystem,
            AccessTokenGenerator tokens,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${ayni.identity.session-ttl:P7D}") Duration sessionTtl) {

        this.accessLinks = accessLinks;
        this.users = users;
        this.sessions = sessions;
        this.academicRecords = academicRecords;
        this.academicSystem = academicSystem;
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
    }

    @Transactional
    public ConfirmAccessResult execute(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new IdentityRuleViolation(
                    "Access token must not be blank");
        }

        Instant now = clock.instant();

        AccessLink accessLink =
                accessLinks
                        .findByTokenHash(tokens.hash(rawToken))
                        .orElseThrow(
                                () ->
                                        new IdentityRuleViolation(
                                                INVALID));

        if (!accessLink.isUsable(now)) {
            throw new IdentityRuleViolation(
                    EXPIRED_OR_USED);
        }

        String tenantId = accessLink.getTenantId();

        if (tenantId == null) {
            throw new IdentityRuleViolation(
                    NO_UNIVERSITY);
        }

        User user =
                switch (accessLink.getPurpose()) {
                    case LOGIN ->
                            confirmExistingStudent(
                                    accessLink,
                                    tenantId,
                                    now);

                    case ACTIVATION ->
                            activateStudent(
                                    accessLink,
                                    tenantId,
                                    now);

                    case COORDINATOR_INVITE ->
                            throw new IdentityRuleViolation(
                                    INVITATION_PENDING);
                };

        return openSession(
                tenantId,
                user,
                now);
    }

    private User confirmExistingStudent(
            AccessLink accessLink,
            String tenantId,
            Instant now) {

        User user =
                users.findByTenantIdAndEmailIgnoreCase(
                                tenantId,
                                accessLink.getEmail())
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "User not found for access link"));

        if (!user.isActive()) {
            throw new IdentityRuleViolation(
                    NOT_ACTIVE);
        }

        consume(
                accessLink,
                now);

        return user;
    }

    private User activateStudent(
            AccessLink accessLink,
            String tenantId,
            Instant now) {

        String email = accessLink.getEmail();

        if (users.existsByTenantIdAndEmailIgnoreCase(
                tenantId,
                email)) {

            throw new IdentityRuleViolation(
                    ACCOUNT_ALREADY_EXISTS);
        }

        String studentCode =
                studentCodeFromEmail(email);

        AcademicProfile profile =
                academicSystem.fetchProfile(
                        tenantId,
                        studentCode);

        /*
         * The potentially external academic lookup happens before the
         * single-use write. If it fails, the link remains available.
         */
        consume(
                accessLink,
                now);

        User user =
                new User(
                        UUID.randomUUID(),
                        tenantId,
                        UserRole.STUDENT,
                        email,
                        studentCode,
                        profile.fullName(),
                        profile.career(),
                        profile.currentTerm(),
                        null,
                        null,
                        UserStatus.ACTIVE,
                        OnboardingStep.PROFILE,
                        now,
                        now,
                        now);

        /*
         * Flush before publishing StudentActivated so a listener that
         * immediately queries Identity sees the student.
         */
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

        /*
         * Skills reacts to StudentActivated and may immediately ask
         * IdentityApi for the approved courses.
         */
        academicRecords.saveAllAndFlush(records);

        events.publishEvent(
                new StudentActivated(
                        tenantId,
                        user.getId(),
                        user.getEmail(),
                        user.getStudentCode(),
                        now));

        return user;
    }

    private ConfirmAccessResult openSession(
            String tenantId,
            User user,
            Instant now) {

        GeneratedAccessToken sessionToken =
                tokens.generate();

        Instant expiresAt =
                now.plus(sessionTtl);

        sessions.save(
                new UserSession(
                        UUID.randomUUID(),
                        tenantId,
                        user.getId(),
                        sessionToken.tokenHash(),
                        expiresAt,
                        now));

        return new ConfirmAccessResult(
                sessionToken.rawToken(),
                tenantId,
                user.getId(),
                expiresAt);
    }

    private void consume(
            AccessLink accessLink,
            Instant now) {

        if (accessLinks.consume(
                accessLink.getId(),
                now)
                == 0) {

            throw new IdentityRuleViolation(
                    EXPIRED_OR_USED);
        }
    }

    private String studentCodeFromEmail(
            String email) {

        int separator = email.indexOf('@');

        if (separator <= 0) {
            throw new IdentityRuleViolation(
                    "Institutional email does not contain a student code");
        }

        return email
                .substring(0, separator)
                .toUpperCase(Locale.ROOT);
    }
}