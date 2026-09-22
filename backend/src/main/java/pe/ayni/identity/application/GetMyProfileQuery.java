package pe.ayni.identity.application;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.infrastructure.UserRepository;

@Service
@Transactional(readOnly = true)
public class GetMyProfileQuery {

    private final UserRepository users;

    public GetMyProfileQuery(UserRepository users) {
        this.users = users;
    }

    public MyProfileView execute(
            String tenantId,
            UUID userId) {

        User user =
                users.findByTenantIdAndId(tenantId, userId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "User not found: " + userId));

        return toView(user);
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