package pe.ayni.identity.application;

import java.util.UUID;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.domain.model.OnboardingStep;
public record MyProfileView(
        UUID id,
        String tenantId,
        UserRole role,
        String email,
        String studentCode,
        String fullName,
        String career,
        String currentTerm,
        String photoUrl,
        String bio,
        UserStatus status,
        OnboardingStep onboardingStep) {}