package pe.ayni.identity.application;

import pe.ayni.identity.UserRole;
import pe.ayni.identity.domain.model.OnboardingStep;

/**
 * One active person of a university, as the demonstration lists them so that the presenter can act
 * as any of them.
 *
 * @param career what the person studies, {@code null} when their profile has none
 * @param onboardingStep where a student is in the onboarding, {@code null} for a coordinator
 */
public record DemoPersonaView(
        String email,
        String fullName,
        UserRole role,
        String universityCode,
        String universityName,
        String career,
        OnboardingStep onboardingStep) {}
