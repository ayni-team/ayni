package pe.ayni.identity.application;

/**
 * The academic system of the universities, as identity needs it (ADR 0006): who a university is,
 * which courses it teaches, and who a student is, looked up by their university and their
 * institutional email.
 *
 * <p>The answer about a student carries the student code the university assigned, so nothing in
 * Ayni has to derive it from the email or know its format. Every answer is read from the academic
 * system when it is asked, never kept from an earlier one.
 */
public interface AcademicSystemPort {

    /**
     * The profile of the student with that institutional email in that university.
     *
     * @throws pe.ayni.identity.domain.model.AcademicSystemNotConfigured when the university has no
     *     academic system configured
     * @throws pe.ayni.identity.domain.model.IdentityRuleViolation when it does not know that email
     * @throws IllegalStateException when the academic system answers something that cannot be read
     *     or does not agree with itself: a mistake of the platform
     */
    AcademicProfile fetchProfile(
            String tenantId,
            String institutionalEmail);

    /**
     * Who the university is and the courses of its curriculum, as its academic system says.
     *
     * @throws pe.ayni.identity.domain.model.AcademicSystemNotConfigured when the university has no
     *     academic system configured
     * @throws IllegalStateException when the academic system answers something that cannot be read
     *     or does not agree with itself: a mistake of the platform
     */
    AcademicUniversity fetchUniversity(
            String code);
}
