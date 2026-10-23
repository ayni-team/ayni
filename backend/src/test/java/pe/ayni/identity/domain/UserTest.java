package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.OnboardingConflict;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;

class UserTest {

    private static final Instant NOW =
            Instant.parse("2026-09-22T05:00:00Z");

    @Test
    @DisplayName("normalizes the institutional email")
    void normalizesInstitutionalEmail() {
        User user =
                new User(
                        UUID.randomUUID(),
                        "UPC",
                        UserRole.STUDENT,
                        "  U202612345@UPC.EDU.PE  ",
                        "U202612345",
                        "Juan Sanchez",
                        "Software Engineering",
                        "7",
                        null,
                        null,
                        UserStatus.ACTIVE,
                        null,
                        NOW,
                        NOW,
                        NOW);

        assertThat(user.getEmail())
                .isEqualTo("u202612345@upc.edu.pe");
    }

    @Test
    @DisplayName("only an ACTIVE user is active")
    void identifiesAnActiveUser() {
        User user =
                new User(
                        UUID.randomUUID(),
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
                        null,
                        NOW,
                        NOW,
                        NOW);

        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("the learning interests complete the onboarding")
    void theLearningInterestsCompleteTheOnboarding() {
        User student = aStudentAt(OnboardingStep.LEARNING_INTERESTS);
        Instant later = NOW.plusSeconds(60);

        student.completeOnboarding(later);

        assertThat(student.getOnboardingStep()).isEqualTo(OnboardingStep.COMPLETED);
        assertThat(student.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("completing a completed onboarding changes nothing")
    void completingACompletedOnboardingChangesNothing() {
        User student = aStudentAt(OnboardingStep.COMPLETED);

        student.completeOnboarding(NOW.plusSeconds(60));

        assertThat(student.getOnboardingStep()).isEqualTo(OnboardingStep.COMPLETED);
        assertThat(student.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @EnumSource(value = OnboardingStep.class, names = {"PROFILE", "ACADEMIC_RECORD"})
    @DisplayName("the onboarding cannot be completed before the profile and the academic record")
    void theOnboardingCannotBeCompletedBeforeTheEarlierSteps(OnboardingStep step) {
        User student = aStudentAt(step);

        assertThatThrownBy(() -> student.completeOnboarding(NOW.plusSeconds(60)))
                .isInstanceOf(OnboardingConflict.class)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(step.name());

        assertThat(student.getOnboardingStep()).isEqualTo(step);
        assertThat(student.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("a student with no step yet has not done the profile")
    void aStudentWithNoStepHasNotDoneTheProfile() {
        User student = aStudentAt(null);

        assertThatThrownBy(() -> student.completeOnboarding(NOW))
                .isInstanceOf(OnboardingConflict.class)
                .hasMessageContaining("PROFILE");
        assertThat(student.getOnboardingStep()).isNull();
    }

    @Test
    @DisplayName("a coordinator has no onboarding to complete")
    void aCoordinatorHasNoOnboardingToComplete() {
        User coordinator =
                new User(
                        UUID.randomUUID(),
                        "UPC",
                        UserRole.COORDINATOR,
                        "coordinator@upc.edu.pe",
                        null,
                        "Carmen Rojas",
                        null,
                        null,
                        null,
                        null,
                        UserStatus.ACTIVE,
                        null,
                        NOW,
                        NOW,
                        NOW);

        assertThatThrownBy(() -> coordinator.completeOnboarding(NOW))
                .isInstanceOf(OnboardingConflict.class);
        assertThat(coordinator.getOnboardingStep()).isNull();
    }

    private static User aStudentAt(OnboardingStep step) {
        return new User(
                UUID.randomUUID(),
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
                NOW,
                NOW,
                NOW);
    }
}