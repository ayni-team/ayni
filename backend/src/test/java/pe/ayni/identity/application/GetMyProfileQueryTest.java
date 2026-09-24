package pe.ayni.identity.application;

import pe.ayni.shared.tenancy.TenantContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.domain.model.OnboardingStep;

class GetMyProfileQueryTest {

    @Test
    void returnsProfileForCurrentTenantUser() {
        UserRepository users =
                mock(UserRepository.class);

        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-22T15:00:00Z");

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
                        "Student bio",
                        UserStatus.ACTIVE,
                        OnboardingStep.PROFILE,
                        now,
                        now,
                        now);

        when(users.findByTenantIdAndId("UPC", userId))
                .thenReturn(Optional.of(user));

        GetMyProfileQuery query =
                new GetMyProfileQuery(users);

        MyProfileView[] holder = new MyProfileView[1];
        TenantContext.runAs("UPC", () -> holder[0] = query.execute(userId));
        MyProfileView result = holder[0];

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.tenantId()).isEqualTo("UPC");
        assertThat(result.bio()).isEqualTo("Student bio");
    }
}