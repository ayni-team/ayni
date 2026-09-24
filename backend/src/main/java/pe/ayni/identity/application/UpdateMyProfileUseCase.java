package pe.ayni.identity.application;

import pe.ayni.shared.tenancy.TenantContext;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.infrastructure.UserRepository;

@Service
public class UpdateMyProfileUseCase {

    private final UserRepository users;
    private final Clock clock;

    public UpdateMyProfileUseCase(
            UserRepository users,
            Clock clock) {

        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public MyProfileView execute(
            UUID userId,
            String photoUrl,
            String bio) {

        String tenantId = TenantContext.require();
        User user =
                users.findByTenantIdAndId(tenantId, userId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "User not found: " + userId));

        var now = clock.instant();

        user.updateProfile(
                normalizeNullable(photoUrl),
                normalizeNullable(bio),
                now);

        user.markProfileCompleted(now);

        return toView(user);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private MyProfileView toView(User user) {
        return new MyProfileView(
                user.getId(),
                user.getTenantId(),
                user.getRole(),
                user.getEmail(),
                user.getStudentCode(),
                user.getFullName(),
                user.getCareer(),
                user.getCurrentTerm(),
                user.getPhotoUrl(),
                user.getBio(),
                user.getStatus(),
                user.getOnboardingStep());
    }
}