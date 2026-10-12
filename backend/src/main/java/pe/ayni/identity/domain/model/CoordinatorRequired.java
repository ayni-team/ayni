package pe.ayni.identity.domain.model;

/**
 * The person who asks is not an active coordinator of the university of the request.
 *
 * <p>Not an {@link IdentityRuleViolation}: the request is well formed and the person is not allowed
 * to make it, so it answers 403 and not 400.
 */
public class CoordinatorRequired extends RuntimeException {

    public CoordinatorRequired(String message) {
        super(message);
    }
}
