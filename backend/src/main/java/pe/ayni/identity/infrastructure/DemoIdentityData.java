package pe.ayni.identity.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
import pe.ayni.shared.demo.DemoCast;
import pe.ayni.shared.demo.DemoCast.Person;
import pe.ayni.shared.demo.DemoCast.Step;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The two universities of the academic directory and the four people the demonstration is told
 * with, so there is somebody to sign in as before anybody activated their account.
 *
 * <p>Everything comes from the documents of {@code backend/academic-directory} (ADR 0006): UPC and
 * USIL are registered with what their documents say, through the same use case as {@code POST
 * /api/v1/admin/universities/from-academic-system}, with five initial credits for thirty days. Of
 * their students only the people of {@link DemoCast} are activated here, thirteen of each
 * university; every other one, Carla Mendoza of UPC among them, activates their account live with
 * the link of their email.
 *
 * <ul>
 *   <li>Ana Torres (UPC) and Lucía Paredes (USIL) are active at the first step of the onboarding, so
 *       a presentation can walk through it.
 *   <li>One student of each university waits at the academic record and another at the learning
 *       interests, so each step of the onboarding can be shown without walking the ones before it.
 *   <li>Everybody else ends the onboarding: their profile and record are done here, through the
 *       real use cases, and {@code DemoSkillsData} takes the last step for them, which enables the
 *       courses the tutors teach.
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

    private static final List<String> UNIVERSITIES = List.of(DemoCast.UPC, DemoCast.USIL);

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
        for (Person person : DemoCast.STUDENTS) {
            TenantContext.runAs(person.university(), () -> activate(person));
        }
    }

    private void activate(Person person) {
        String tenant = person.university();
        if (users.findByTenantIdAndId(tenant, person.id()).isPresent()
                || users.existsByTenantIdAndEmailIgnoreCase(tenant, person.email())) {
            return;
        }

        Instant now = clock.instant();
        AcademicProfile profile = activation.admit(tenant, person.email());
        activation.create(tenant, person.id(), person.email(), profile, now);

        // The steps of the onboarding the student takes, through the use cases of their endpoints:
        // the profile, then the record, which leaves them at the interests.
        if (person.step() != Step.PROFILE) {
            updateProfile.execute(person.id(), null, null);
        }
        if (person.step() == Step.LEARNING_INTERESTS || person.step() == Step.COMPLETED) {
            importAcademicRecord.execute(person.id());
        }
        log.info("Demo person {} of {} activated at {}", person.id(), tenant, person.step());
    }
}
