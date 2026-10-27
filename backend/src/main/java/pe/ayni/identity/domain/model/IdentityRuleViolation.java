package pe.ayni.identity.domain.model;

/** A business rule of the Identity module was violated. */
public class IdentityRuleViolation extends RuntimeException {

    public IdentityRuleViolation(String message) {
        super(message);
    }
}