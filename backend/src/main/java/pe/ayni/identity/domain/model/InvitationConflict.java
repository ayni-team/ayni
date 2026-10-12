package pe.ayni.identity.domain.model;

/**
 * A coordinator invitation cannot be issued or accepted because the current state refuses it.
 *
 * <p>Not an {@link IdentityRuleViolation}: the request is well formed, so it answers 409 and not
 * 400.
 */
public class InvitationConflict extends RuntimeException {

    private InvitationConflict(String message) {
        super(message);
    }

    public static InvitationConflict universityNotActive() {
        return new InvitationConflict("The university is not active");
    }

    public static InvitationConflict emailAlreadyHasUser() {
        return new InvitationConflict("A user with this email already exists in the university");
    }

    public static InvitationConflict alreadyUsed() {
        return new InvitationConflict("This coordinator invitation was already used");
    }
}
