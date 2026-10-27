package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.AccessPurpose;
import pe.ayni.identity.domain.model.AcademicRecord;
import pe.ayni.identity.domain.model.CoordinatorRequired;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.domain.model.InvitationConflict;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.AccessRole;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.AcademicRecordRepository;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.CoordinatorActivated;
import pe.ayni.shared.events.StudentActivated;

/** US38 confirmation without Spring. */
class ConfirmAccessUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-09-24T15:00:00Z");

    private static final String LINK_HASH =
            "a".repeat(64);

    private final UUID userId =
            UUID.randomUUID();

    private AccessLinkRepository accessLinks;
    private UserRepository users;
    private PlatformAdminRepository admins;
    private UserSessionRepository sessions;
    private AcademicRecordRepository academicRecords;
    private AcademicSystemPort academicSystem;
    private AccessTokenGenerator tokens;
    private ApplicationEventPublisher events;

    private ConfirmAccessUseCase useCase;

    @BeforeEach
    void setUp() {
        accessLinks =
                mock(AccessLinkRepository.class);

        users =
                mock(UserRepository.class);

        admins =
                mock(PlatformAdminRepository.class);

        sessions =
                mock(UserSessionRepository.class);

        academicRecords =
                mock(AcademicRecordRepository.class);

        academicSystem =
                mock(AcademicSystemPort.class);

        tokens =
                mock(AccessTokenGenerator.class);

        events =
                mock(ApplicationEventPublisher.class);

        when(tokens.hash("raw-access-token"))
                .thenReturn(LINK_HASH);

        when(tokens.generate())
                .thenReturn(
                        new GeneratedAccessToken(
                                "raw-session-token",
                                "b".repeat(64)));

        useCase =
                new ConfirmAccessUseCase(
                        accessLinks,
                        users,
                        admins,
                        sessions,
                        new StudentActivation(
                                users,
                                academicRecords,
                                academicSystem),
                        tokens,
                        events,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC),
                        Duration.ofDays(7));
    }

    private AccessLink link(
            String tenantId,
            AccessPurpose purpose,
            Instant expiresAt) {

        return link(
                tenantId,
                "student@upc.edu.pe",
                purpose,
                expiresAt);
    }

    private AccessLink link(
            String tenantId,
            String email,
            AccessPurpose purpose,
            Instant expiresAt) {

        AccessLink link =
                new AccessLink(
                        UUID.randomUUID(),
                        tenantId,
                        email,
                        purpose,
                        LINK_HASH,
                        expiresAt,
                        "127.0.0.1",
                        NOW.minusSeconds(60));

        when(accessLinks.findByTokenHash(LINK_HASH))
                .thenReturn(
                        Optional.of(link));

        return link;
    }

    private AccessLink loginLink() {
        return link(
                "UPC",
                AccessPurpose.LOGIN,
                NOW.plusSeconds(600));
    }

    private void aStudent(
            UserStatus status) {

        when(
                users.findByTenantIdAndEmailIgnoreCase(
                        "UPC",
                        "student@upc.edu.pe"))
                .thenReturn(
                        Optional.of(
                                new User(
                                        userId,
                                        "UPC",
                                        UserRole.STUDENT,
                                        "student@upc.edu.pe",
                                        "U202612345",
                                        "Student",
                                        "Software Engineering",
                                        "2026-2",
                                        null,
                                        null,
                                        status,
                                        null,
                                        NOW.minusSeconds(3600),
                                        NOW.minusSeconds(3600),
                                        NOW.minusSeconds(3600))));
    }

    private void assertRefused(
            String message) {

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "raw-access-token"))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessage(message);

        verify(sessions, never())
                .save(any());
    }

    @Test
    @DisplayName(
            "a sign in link opens a session in the university written on the link")
    void confirmsLoginAndCreatesSession() {

        AccessLink link =
                loginLink();

        aStudent(
                UserStatus.ACTIVE);

        when(
                accessLinks.consume(
                        link.getId(),
                        NOW))
                .thenReturn(1);

        ConfirmAccessResult result =
                useCase.execute(
                        "raw-access-token");

        assertThat(result.sessionToken())
                .isEqualTo(
                        "raw-session-token");

        assertThat(result.tenantId())
                .isEqualTo("UPC");

        assertThat(result.userId())
                .isEqualTo(userId);

        assertThat(result.expiresAt())
                .isEqualTo(
                        NOW.plus(
                                Duration.ofDays(7)));

        ArgumentCaptor<UserSession> captor =
                ArgumentCaptor.forClass(
                        UserSession.class);

        verify(sessions)
                .save(captor.capture());

        assertThat(
                captor.getValue()
                        .getTenantId())
                .isEqualTo("UPC");

        assertThat(
                captor.getValue()
                        .getUserId())
                .isEqualTo(userId);

        assertThat(
                captor.getValue()
                        .getTokenHash())
                .isEqualTo(
                        "b".repeat(64));
    }

    @Test
    @DisplayName(
            "first access activates a student with the academic profile")
    void activatesNewStudent() {

        String email =
                "u202500003@upc.edu.pe";

        AccessLink link =
                link(
                        "UPC",
                        email,
                        AccessPurpose.ACTIVATION,
                        NOW.plusSeconds(600));

        when(
                users.existsByTenantIdAndEmailIgnoreCase(
                        "UPC",
                        email))
                .thenReturn(false);

        when(
                academicSystem.fetchProfile(
                        "UPC",
                        "u202500003@upc.edu.pe"))
                .thenReturn(
                        new AcademicProfile(
                                "U202500003",
                                "Carla Mendoza",
                                "Software Engineering",
                                3,
                                "2026-2",
                                AcademicStatus.ACTIVE,
                                List.of(
                                        new AcademicCourseData(
                                                "1ASI0657",
                                                "Software Architecture Fundamentals",
                                                new BigDecimal("18.00"),
                                                "2026-1"),
                                        new AcademicCourseData(
                                                "1ASI0616",
                                                "Databases I",
                                                new BigDecimal("16.00"),
                                                "2025-2"))));

        when(
                accessLinks.consume(
                        link.getId(),
                        NOW))
                .thenReturn(1);

        ConfirmAccessResult result =
                useCase.execute(
                        "raw-access-token");

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(
                        User.class);

        verify(users)
                .saveAndFlush(
                        userCaptor.capture());

        User created =
                userCaptor.getValue();

        assertThat(created.getTenantId())
                .isEqualTo("UPC");

        assertThat(created.getEmail())
                .isEqualTo(email);

        assertThat(created.getStudentCode())
                .isEqualTo("U202500003");

        assertThat(created.getFullName())
                .isEqualTo("Carla Mendoza");

        assertThat(created.getCareer())
                .isEqualTo(
                        "Software Engineering");

        assertThat(created.getCurrentTerm())
                .isEqualTo("2026-2");

        assertThat(created.getCurrentCycle())
                .isEqualTo(3);

        assertThat(created.getStatus())
                .isEqualTo(
                        UserStatus.ACTIVE);

        assertThat(created.getOnboardingStep())
                .isEqualTo(
                        OnboardingStep.PROFILE);

        assertThat(created.getActivatedAt())
                .isEqualTo(NOW);

        verify(academicRecords)
                .saveAllAndFlush(any());

        ArgumentCaptor<StudentActivated> eventCaptor =
                ArgumentCaptor.forClass(
                        StudentActivated.class);

        verify(events)
                .publishEvent(
                        eventCaptor.capture());

        StudentActivated event =
                eventCaptor.getValue();

        assertThat(event.tenantId())
                .isEqualTo("UPC");

        assertThat(event.userId())
                .isEqualTo(
                        created.getId());

        assertThat(event.email())
                .isEqualTo(email);

        assertThat(event.studentCode())
                .isEqualTo(
                        "U202500003");

        assertThat(event.occurredOn())
                .isEqualTo(NOW);

        assertThat(result.tenantId())
                .isEqualTo("UPC");

        assertThat(result.userId())
                .isEqualTo(
                        created.getId());

        verify(sessions)
                .save(any(UserSession.class));
    }

    @Test
    @DisplayName(
            "academic lookup failure leaves the activation link unused")
    void academicLookupFailureLeavesLinkUnused() {

        String email =
                "u202599999@upc.edu.pe";

        AccessLink link =
                link(
                        "UPC",
                        email,
                        AccessPurpose.ACTIVATION,
                        NOW.plusSeconds(600));

        when(
                academicSystem.fetchProfile(
                        "UPC",
                        "u202599999@upc.edu.pe"))
                .thenThrow(
                        new IdentityRuleViolation(
                                "The student was not found in the academic system"));

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "raw-access-token"))
                .isInstanceOf(
                        IdentityRuleViolation.class)
                .hasMessageContaining(
                        "not found");

        verify(accessLinks, never())
                .consume(
                        link.getId(),
                        NOW);

        verify(users, never())
                .saveAndFlush(any());

        verify(events, never())
                .publishEvent(
                        any(StudentActivated.class));

        verify(sessions, never())
                .save(any());
    }

    @Test
    @DisplayName(
            "a student the university no longer considers its own is not activated, and the link stays unused")
    void aStudentNoLongerEnrolledIsNotActivated() {

        for (AcademicStatus status : List.of(AcademicStatus.GRADUATED, AcademicStatus.INACTIVE)) {
            String email =
                    "u202300022@upc.edu.pe";

            AccessLink link =
                    link(
                            "UPC",
                            email,
                            AccessPurpose.ACTIVATION,
                            NOW.plusSeconds(600));

            when(
                    academicSystem.fetchProfile(
                            "UPC",
                            email))
                    .thenReturn(
                            new AcademicProfile(
                                    "U202300022",
                                    "Carla Lazo",
                                    "Ingeniería de Software",
                                    10,
                                    "2026-2",
                                    status,
                                    List.of()));

            assertRefused(
                    StudentActivation.NOT_ENROLLED);

            verify(accessLinks, never())
                    .consume(
                            link.getId(),
                            NOW);
        }

        verify(users, never())
                .saveAndFlush(any());

        verify(academicRecords, never())
                .saveAllAndFlush(any());

        verify(events, never())
                .publishEvent(
                        any(StudentActivated.class));

        verify(sessions, never())
                .save(any());
    }

    @Test
    @DisplayName(
            "an activation link cannot create a second account")
    void activationCannotCreateSecondAccount() {

        String email =
                "u202500003@upc.edu.pe";

        AccessLink link =
                link(
                        "UPC",
                        email,
                        AccessPurpose.ACTIVATION,
                        NOW.plusSeconds(600));

        when(
                users.existsByTenantIdAndEmailIgnoreCase(
                        "UPC",
                        email))
                .thenReturn(true);

        assertRefused(
                StudentActivation.ACCOUNT_ALREADY_EXISTS);

        verify(accessLinks, never())
                .consume(
                        link.getId(),
                        NOW);

        verify(academicSystem, never())
                .fetchProfile(
                        "UPC",
                        "u202500003@upc.edu.pe");

        verify(events, never())
                .publishEvent(
                        any(StudentActivated.class));
    }

    @Test
    @DisplayName(
            "a link another confirmation consumed first opens nothing")
    void aLinkConsumedMeanwhileOpensNothing() {

        AccessLink link =
                loginLink();

        aStudent(
                UserStatus.ACTIVE);

        when(
                accessLinks.consume(
                        link.getId(),
                        NOW))
                .thenReturn(0);

        assertRefused(
                ConfirmAccessUseCase.EXPIRED_OR_USED);
    }

    @Test
    @DisplayName(
            "an unknown token is refused")
    void anUnknownTokenIsRefused() {

        when(
                accessLinks.findByTokenHash(
                        LINK_HASH))
                .thenReturn(
                        Optional.empty());

        assertRefused(
                ConfirmAccessUseCase.INVALID);
    }

    @Test
    @DisplayName(
            "an expired link is refused before anything is written")
    void anExpiredLinkIsRefused() {

        AccessLink link =
                link(
                        "UPC",
                        AccessPurpose.LOGIN,
                        NOW.minusSeconds(1));

        assertRefused(
                ConfirmAccessUseCase.EXPIRED_OR_USED);

        verify(accessLinks, never())
                .consume(
                        link.getId(),
                        NOW);
    }

    private User invitedCoordinator(
            UserRole role,
            UserStatus status) {

        User coordinator =
                new User(
                        userId,
                        "UPC",
                        role,
                        "coordinator@upc.edu.pe",
                        null,
                        "Maria Coordinator",
                        null,
                        null,
                        null,
                        null,
                        status,
                        null,
                        null,
                        NOW.minusSeconds(3600),
                        NOW.minusSeconds(3600));

        when(
                users.findByTenantIdAndEmailIgnoreCase(
                        "UPC",
                        "coordinator@upc.edu.pe"))
                .thenReturn(
                        Optional.of(
                                coordinator));

        return coordinator;
    }

    @Test
    @DisplayName(
            "a coordinator invitation activates the coordinator once and opens a session")
    void invitationActivatesTheCoordinator() {

        AccessLink link =
                link(
                        "UPC",
                        "coordinator@upc.edu.pe",
                        AccessPurpose.COORDINATOR_INVITE,
                        NOW.plusSeconds(600));

        User coordinator =
                invitedCoordinator(
                        UserRole.COORDINATOR,
                        UserStatus.PENDING);

        when(
                accessLinks.consume(
                        link.getId(),
                        NOW))
                .thenReturn(1);

        ConfirmAccessResult result =
                useCase.execute(
                        "raw-access-token");

        assertThat(coordinator.getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(coordinator.getActivatedAt())
                .isEqualTo(NOW);

        assertThat(result.userId())
                .isEqualTo(userId);

        verify(users)
                .saveAndFlush(
                        coordinator);

        verify(events)
                .publishEvent(
                        new CoordinatorActivated(
                                "UPC",
                                userId,
                                NOW));

        verify(sessions)
                .save(any());
    }

    @Test
    @DisplayName(
            "a coordinator invitation that was already used is a conflict")
    void usedInvitationIsAConflict() {

        AccessLink link =
                link(
                        "UPC",
                        "coordinator@upc.edu.pe",
                        AccessPurpose.COORDINATOR_INVITE,
                        NOW.plusSeconds(600));

        link.consume(
                NOW.minusSeconds(30));

        invitedCoordinator(
                UserRole.COORDINATOR,
                UserStatus.ACTIVE);

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "raw-access-token"))
                .isInstanceOf(
                        InvitationConflict.class);

        verify(sessions, never())
                .save(any());

        verify(events, never())
                .publishEvent(
                        any(CoordinatorActivated.class));
    }

    @Test
    @DisplayName(
            "a coordinator invitation lost to a concurrent confirmation is a conflict")
    void invitationLostToAConcurrentConfirmationIsAConflict() {

        AccessLink link =
                link(
                        "UPC",
                        "coordinator@upc.edu.pe",
                        AccessPurpose.COORDINATOR_INVITE,
                        NOW.plusSeconds(600));

        User coordinator =
                invitedCoordinator(
                        UserRole.COORDINATOR,
                        UserStatus.PENDING);

        when(
                accessLinks.consume(
                        link.getId(),
                        NOW))
                .thenReturn(0);

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "raw-access-token"))
                .isInstanceOf(
                        InvitationConflict.class);

        assertThat(coordinator.getStatus())
                .isEqualTo(UserStatus.PENDING);

        verify(sessions, never())
                .save(any());

        verify(events, never())
                .publishEvent(
                        any(CoordinatorActivated.class));
    }

    @Test
    @DisplayName(
            "a coordinator invitation of someone who is not a coordinator is forbidden")
    void invitationOfANonCoordinatorIsForbidden() {

        AccessLink link =
                link(
                        "UPC",
                        "coordinator@upc.edu.pe",
                        AccessPurpose.COORDINATOR_INVITE,
                        NOW.plusSeconds(600));

        invitedCoordinator(
                UserRole.STUDENT,
                UserStatus.PENDING);

        assertThatThrownBy(
                () ->
                        useCase.execute(
                                "raw-access-token"))
                .isInstanceOf(
                        CoordinatorRequired.class);

        verify(accessLinks, never())
                .consume(
                        link.getId(),
                        NOW);

        verify(sessions, never())
                .save(any());
    }

    @Test
    @DisplayName(
            "a link without a university whose address is no administrator is refused")
    void aLinkWithoutUniversityIsRefused() {

        link(
                null,
                AccessPurpose.LOGIN,
                NOW.plusSeconds(600));

        assertRefused(
                ConfirmAccessUseCase.NO_UNIVERSITY);
    }

    @Test
    @DisplayName(
            "an account that is not active cannot sign in")
    void anInactiveAccountIsRefused() {

        AccessLink link =
                loginLink();

        aStudent(
                UserStatus.RESTRICTED);

        assertRefused(
                ConfirmAccessUseCase.NOT_ACTIVE);

        verify(accessLinks, never())
                .consume(
                        link.getId(),
                        NOW);
    }

    @Test
    @DisplayName(
            "a blank token is refused")
    void aBlankTokenIsRefused() {

        assertThatThrownBy(
                () ->
                        useCase.execute("  "))
                .isInstanceOf(
                        IdentityRuleViolation.class);
    }

    @Test
    @DisplayName(
            "a link without a university opens a session with no university for its administrator")
    void anAdministratorsLinkOpensAnAdministratorsSession() {

        AccessLink link =
                link(
                        null,
                        "admin@ayni.local",
                        AccessPurpose.LOGIN,
                        NOW.plusSeconds(600));

        UUID adminId = UUID.randomUUID();
        when(admins.findByEmailIgnoreCase("admin@ayni.local"))
                .thenReturn(
                        Optional.of(
                                new PlatformAdmin(
                                        adminId,
                                        "admin@ayni.local",
                                        "Admin",
                                        PlatformAdminStatus.ACTIVE,
                                        NOW)));
        when(accessLinks.consume(link.getId(), NOW)).thenReturn(1);

        ConfirmAccessResult result = useCase.execute("raw-access-token");

        assertThat(result.tenantId()).isNull();
        assertThat(result.userId()).isEqualTo(adminId);
        assertThat(result.role()).isEqualTo(AccessRole.PLATFORM_ADMIN);
        assertThat(result.sessionToken()).isEqualTo("raw-session-token");

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getAdminId()).isEqualTo(adminId);
        assertThat(saved.getValue().getTenantId()).isNull();
        assertThat(saved.getValue().getUserId()).isNull();
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }
}
