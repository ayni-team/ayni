package pe.ayni.identity.domain.model;

/**
 * The credit policy cannot be changed because the state of the university refuses it: it is
 * suspended, or it has no baseline policy to supersede.
 *
 * <p>Not an {@link IdentityRuleViolation}: the request is well formed, so it answers 409 and not
 * 400.
 */
public class CreditPolicyConflict extends RuntimeException {

    public CreditPolicyConflict(String message) {
        super(message);
    }
}
