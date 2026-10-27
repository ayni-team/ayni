package pe.ayni.identity.domain.model;

/**
 * The onboarding cannot move because the step the person is in refuses it: the profile or the
 * academic record is still missing, or the person is a coordinator, who has no onboarding.
 *
 * <p>An {@link IllegalStateException}, which is what {@code IdentityApi.completeOnboarding} promises
 * to throw, so another module can tell the refusal apart without importing this class. The request
 * is well formed, so it answers 409.
 */
public class OnboardingConflict extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private OnboardingConflict(String message) {
        super(message);
    }

    public static OnboardingConflict stepsMissing(OnboardingStep current) {
        return new OnboardingConflict(
                "The onboarding is at step %s: the profile and the academic record come before the interests"
                        .formatted(current == null ? OnboardingStep.PROFILE : current));
    }

    public static OnboardingConflict notAStudent() {
        return new OnboardingConflict("A coordinator has no onboarding");
    }
}
