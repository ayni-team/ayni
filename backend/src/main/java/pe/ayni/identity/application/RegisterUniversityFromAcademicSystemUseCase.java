package pe.ayni.identity.application;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.AcademicSystemNotConfigured;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

/**
 * TS10 by its academic system (ADR 0006, amended 2026-10-23): the administrator names a university
 * by its code, and it is registered with what its academic system says it is.
 *
 * <p>The name, the logo, the colours, the email domains, the time zone and the minimum teaching
 * grade come from the university's document; the administrator gives only the initial credits and
 * how long they last, which are Ayni's and not the university's. Registering itself is {@link
 * RegisterUniversityUseCase}, unchanged: the same lock, the same refusal of a code or a domain
 * already taken, the same baseline credit policy and the same {@code UniversityRegistered}.
 *
 * <p>The curriculum is not loaded here. A coordinator of the university synchronises it, which keeps
 * the courses with skills, where they belong.
 */
@Service
public class RegisterUniversityFromAcademicSystemUseCase {

    private final AcademicSystemPort academicSystem;
    private final RegisterUniversityUseCase registerUniversity;

    RegisterUniversityFromAcademicSystemUseCase(
            AcademicSystemPort academicSystem,
            RegisterUniversityUseCase registerUniversity) {

        this.academicSystem = academicSystem;
        this.registerUniversity = registerUniversity;
    }

    /**
     * @throws NoSuchElementException when the code names no document of the academic system
     * @throws IdentityRuleViolation when the code is blank or a value is not valid
     * @throws pe.ayni.identity.domain.model.UniversityAlreadyRegistered when the code or a domain
     *     already belongs to a university
     */
    @Transactional
    public UniversityAdminView execute(
            UUID administratorId,
            String code,
            int initialCredits,
            int initialCreditValidityDays) {

        if (code == null || code.isBlank()) {
            throw new IdentityRuleViolation("University code must not be blank");
        }

        AcademicUniversity university;
        try {
            university = academicSystem.fetchUniversity(code);
        } catch (AcademicSystemNotConfigured missing) {
            // Here the code is the resource the request names, and it does not exist.
            throw new NoSuchElementException(
                    "The academic system has no university with this code");
        }

        return registerUniversity.execute(
                administratorId,
                university.code(),
                university.name(),
                university.logoUrl(),
                university.primaryColor(),
                university.secondaryColor(),
                university.emailDomains(),
                university.minimumTeachingGrade(),
                university.timezone(),
                initialCredits,
                initialCreditValidityDays);
    }
}
