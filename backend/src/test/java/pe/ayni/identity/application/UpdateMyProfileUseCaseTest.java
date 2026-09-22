package pe.ayni.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
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
    void updatesOnlyEditableProfileFields() {
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

        MyProfileView result =
                useCase.execute(
                        "UPC",
                        userId,
                        "https://example.test/photo.jpg",
                        "Software Engineering student");

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

        assertThat(user.getUpdatedAt())
                .isEqualTo(NOW);
        assertThat(result.onboardingStep())
                .isEqualTo(OnboardingStep.ACADEMIC_RECORD);
    }
}