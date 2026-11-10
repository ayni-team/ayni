package pe.ayni.identity.application;

import pe.ayni.shared.tenancy.TenantContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.domain.model.OnboardingStep;

class UpdateMyProfileUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-09-22T15:00:00Z");

    @Test
    @DisplayName("Complete the profile step")
    void completingTheProfileMovesToTheAcademicRecord() {
        User[] user = new User[1];
        MyProfileView result = updateTheProfile(user);

        assertThat(result.photoUrl())
                .isEqualTo("https://example.test/photo.jpg");

        assertThat(result.bio())
                .isEqualTo("Software Engineering student");

        assertThat(user[0].getUpdatedAt())
                .isEqualTo(NOW);
        assertThat(result.onboardingStep())
                .isEqualTo(OnboardingStep.ACADEMIC_RECORD);
    }

    @Test
    @DisplayName("Academic fields remain read-only")
    void updatesOnlyEditableProfileFields() {
        MyProfileView result = updateTheProfile(new User[1]);

        assertThat(result.photoUrl())
                .isEqualTo("https://example.test/photo.jpg");

        assertThat(result.bio())
                .isEqualTo("Software Engineering student");

        assertThat(result.email())
                .isEqualTo("u202612345@upc.edu.pe");

        assertThat(result.studentCode())
                .isEqualTo("U202612345");

        assertThat(result.career())
                .isEqualTo("Software Engineering");

        assertThat(result.currentTerm())
                .isEqualTo("7");
    }

    private static MyProfileView updateTheProfile(User[] userHolder) {
        UserRepository users =
                mock(UserRepository.class);

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
                        null,
                        NOW.minusSeconds(60),
                        NOW.minusSeconds(60),
                        NOW.minusSeconds(60));

        when(users.findByTenantIdAndId(
                "UPC",
                userId))
                .thenReturn(Optional.of(user));

        UpdateMyProfileUseCase useCase =
                new UpdateMyProfileUseCase(
                        users,
                        Clock.fixed(NOW, ZoneOffset.UTC));

        MyProfileView[] holder = new MyProfileView[1];
        TenantContext.runAs(
                "UPC",
                () ->
                        holder[0] =
                                useCase.execute(
                                        userId,
                                        "https://example.test/photo.jpg",
                                        "Software Engineering student"));
        userHolder[0] = user;
        return holder[0];
    }
}
