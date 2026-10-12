package pe.ayni.identity.domain.model;

/**
 * A university cannot be registered because the current state already holds its code or one of its
 * email domains.
 *
 * <p>Not an {@link IdentityRuleViolation}: the request is well formed and the state refuses it, so
 * it answers 409 and not 400.
 */
public class UniversityAlreadyRegistered extends RuntimeException {

    private UniversityAlreadyRegistered(String message) {
        super(message);
    }

    public static UniversityAlreadyRegistered code() {
        return new UniversityAlreadyRegistered(
                "A university with this code already exists");
    }

    public static UniversityAlreadyRegistered emailDomain() {
        return new UniversityAlreadyRegistered(
                "An institutional email domain is already registered");
    }
}
