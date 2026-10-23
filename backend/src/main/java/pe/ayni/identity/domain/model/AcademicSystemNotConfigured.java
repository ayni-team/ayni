package pe.ayni.identity.domain.model;

/**
 * The university has no academic system configured: in the simulated one, no document of its own
 * (ADR 0006).
 *
 * <p>An {@link IdentityRuleViolation}, so an activation or an import refused for it answers 400 as
 * it always did and leaves the link unused. Registering a university from its academic system
 * turns it into a 404 instead, since there the code names a document that does not exist.
 */
public class AcademicSystemNotConfigured extends IdentityRuleViolation {

    public AcademicSystemNotConfigured() {
        super("The academic system is not configured for this university");
    }
}
