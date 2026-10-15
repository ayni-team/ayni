package pe.ayni.identity.application;

/**
 * The academic system of the universities, as identity needs it (ADR 0006): who a student is,
 * looked up by their university and their institutional email.
 *
 * <p>The answer carries the student code the university assigned, so nothing in Ayni has to
 * derive it from the email or know its format.
 */
public interface AcademicSystemPort {

    /**
     * The profile of the student with that institutional email in that university.
     *
     * @throws pe.ayni.identity.domain.model.IdentityRuleViolation when the university has no
     *     academic system configured, or it does not know that email
     */
    AcademicProfile fetchProfile(
            String tenantId,
            String institutionalEmail);
}
