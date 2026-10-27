package pe.ayni.identity.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.ayni.identity.application.AcademicProfile;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.identity.application.RegisterUniversityFromAcademicSystemUseCase;
import pe.ayni.identity.application.StudentActivation;
import pe.ayni.identity.application.UpdateMyProfileUseCase;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The two universities of the academic directory and the four people the demonstration is told
 * with, so there is somebody to sign in as before anybody activated their account.
 *
 * <p>Everything comes from the documents of {@code backend/academic-directory} (ADR 0006): UPC and
 * USIL are registered with what their documents say, through the same use case as {@code POST
 * /api/v1/admin/universities/from-academic-system}, with five initial credits for thirty days. Of
 * their students only the four people of the demonstration are activated here; every other one,
 * Carla Mendoza of UPC among them, activates their account live with the link of their email.
 *
 * <ul>
 *   <li>Ana Torres (UPC) and Lucía Paredes (USIL) are active at the first step of the onboarding, so
 *       a presentation can walk through it.
 *   <li>Bruno Salas (UPC) and Mateo Ríos (USIL) are the tutors: their profile and record are done
 *       here, through the real use cases, and {@code DemoSkillsData} takes the last step for them,
 *       which enables the courses their grades justify.
 * </ul>
 *
 * <p>The activation is the one of an access link, {@link StudentActivation}, without the link:
 * there is no email to open here, and the people need fixed identifiers, which the other modules'
 * demo data refer to. {@code StudentActivated} is not published, as it never was for these
 * students, so the wallets they show are the ones {@code DemoWalletData} writes and not the
 * university's initial grant.
 *
 * <p>It runs first among the demonstration data, and once: a university already registered and a
 * person already there are left alone, so a database seeded before these documents keeps what it
 * has.
 */
@Component
@Profile("dev")
@Order(1)
public class DemoIdentityData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoIdentityData.class);

    static final int INITIAL_CREDITS = 5;
    static final int CREDIT_VALIDITY_DAYS = 30;

    /** The same student as in {@code DemoWalletData}. */
    static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");

    /** The other student of {@code DemoWalletData}, and the tutor of {@code DemoBookingData}. */
    static final UUID BRUNO = UUID.fromString("22222222-2222-4222-8222-222222222222");

    /** The student of the second university. */
    static final UUID LUCIA = UUID.fromString("66666666-6666-4666-8666-666666666666");

    /** The tutor of the second university. */
    static final UUID MATEO = UUID.fromString("77777777-7777-4777-8777-777777777777");

    /** One person of the demonstration and how far into the onboarding they start. */
    private record Persona(String tenant, UUID id, String email, boolean tutor) {}

    private static final List<String> UNIVERSITIES = List.of("UPC", "USIL");

    private static final List<Persona> PEOPLE =
            List.of(
                    new Persona("UPC", ANA, "u202400001@upc.edu.pe", false),
                    new Persona("UPC", BRUNO, "u202300002@upc.edu.pe", true),
                    new Persona("USIL", LUCIA, "lucia.paredes@usil.edu.pe", false),
                    new Persona("USIL", MATEO, "mateo.rios@usil.edu.pe", true));

    private final TenantRepository tenants;
    private final UserRepository users;
    private final RegisterUniversityFromAcademicSystemUseCase registerFromAcademicSystem;
    private final StudentActivation activation;
    private final UpdateMyProfileUseCase updateProfile;
    private final ImportAcademicRecordUseCase importAcademicRecord;
    private final Clock clock;

    public DemoIdentityData(
            TenantRepository tenants,
            UserRepository users,
            RegisterUniversityFromAcademicSystemUseCase registerFromAcademicSystem,
            StudentActivation activation,
            UpdateMyProfileUseCase updateProfile,
            ImportAcademicRecordUseCase importAcademicRecord,
            Clock clock) {

        this.tenants = tenants;
        this.users = users;
        this.registerFromAcademicSystem = registerFromAcademicSystem;
        this.activation = activation;
        this.updateProfile = updateProfile;
        this.importAcademicRecord = importAcademicRecord;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String university : UNIVERSITIES) {
            if (tenants.findByCode(university).isEmpty()) {
                // As the administrator, who has no university: registering binds the new one.
                registerFromAcademicSystem.execute(
                        DemoPlatformAdminData.ADMIN,
                        university,
                        INITIAL_CREDITS,
                        CREDIT_VALIDITY_DAYS);
                log.info("Demo university {} registered from its academic document", university);
            }
        }

        // Row level security (TS01): each person is written, and looked for, as their university's.
        for (Persona persona : PEOPLE) {
            TenantContext.runAs(persona.tenant(), () -> activate(persona));
        }
    }

    private void activate(Persona persona) {
        if (users.findByTenantIdAndId(persona.tenant(), persona.id()).isPresent()
                || users.existsByTenantIdAndEmailIgnoreCase(persona.tenant(), persona.email())) {
            return;
        }

        Instant now = clock.instant();
        AcademicProfile profile = activation.admit(persona.tenant(), persona.email());
        activation.create(persona.tenant(), persona.id(), persona.email(), profile, now);

        if (persona.tutor()) {
            // The two steps of the onboarding the student takes, through the use cases of their
            // endpoints: the profile, then the record, which leaves them at the interests.
            updateProfile.execute(persona.id(), null, null);
            importAcademicRecord.execute(persona.id());
        }
        log.info("Demo person {} of {} activated", persona.id(), persona.tenant());
    }
}
