package pe.ayni.identity.infrastructure;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * A university and two students to look at while signing in does not exist yet.
 *
 * <p>The students are the same ones {@code DemoWalletData} gives credits to, so every module's demo
 * data describes the same people. Ana starts at the first onboarding step, and the threshold of 13
 * lets two of the three courses {@code MockAcademicSystemAdapter} reports be offered. Bruno is
 * past the academic record step of onboarding, with his record imported, which is what lets {@code
 * DemoBookingData} enable him as a tutor so that Ana has somebody to book.
 *
 * <p>It runs once: a second start finds the rows already there and leaves them alone.
 */
@Component
@Profile("dev")
public class DemoIdentityData implements ApplicationRunner {

    private static final String TENANT = "UPC";

    /** The same student as in {@code DemoWalletData}. */
    private static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");

    /** The other student of {@code DemoWalletData}, and the tutor of {@code DemoBookingData}. */
    private static final UUID BRUNO = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final TenantRepository tenants;
    private final UserRepository users;
    private final ImportAcademicRecordUseCase importAcademicRecord;
    private final Clock clock;

    public DemoIdentityData(
            TenantRepository tenants,
            UserRepository users,
            ImportAcademicRecordUseCase importAcademicRecord,
            Clock clock) {

        this.tenants = tenants;
        this.users = users;
        this.importAcademicRecord = importAcademicRecord;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant now = clock.instant();

        if (tenants.findByCode(TENANT).isEmpty()) {
            tenants.save(
                    new Tenant(
                            UUID.fromString("00000000-0000-0000-0000-000000000001"),
                            TENANT,
                            "Universidad Peruana de Ciencias Aplicadas",
                            null,
                            "#D50000",
                            "#FFFFFF",
                            List.of("upc.edu.pe"),
                            new BigDecimal("13.00"),
                            ZoneId.of("America/Lima"),
                            TenantStatus.ACTIVE,
                            now));
        }

        if (users.findByTenantIdAndId(TENANT, ANA).isEmpty()) {
            users.save(
                    new User(
                            ANA,
                            TENANT,
                            UserRole.STUDENT,
                            "u202400001@upc.edu.pe",
                            "U202400001",
                            "Ana Torres",
                            "Software Engineering",
                            "2026-2",
                            null,
                            null,
                            UserStatus.ACTIVE,
                            OnboardingStep.PROFILE,
                            now,
                            now,
                            now));
        }

        if (users.findByTenantIdAndId(TENANT, BRUNO).isEmpty()) {
            users.save(
                    new User(
                            BRUNO,
                            TENANT,
                            UserRole.STUDENT,
                            "u202300002@upc.edu.pe",
                            "U202300002",
                            "Bruno Salas",
                            "Software Engineering",
                            "2026-2",
                            null,
                            null,
                            UserStatus.ACTIVE,
                            OnboardingStep.ACADEMIC_RECORD,
                            now,
                            now,
                            now));
            // Through the real use case, the same one US40's onboarding step calls: it imports the
            // record the academic system reports and moves him to the next step.
            TenantContext.runAs(TENANT, () -> importAcademicRecord.execute(BRUNO));
        }
    }
}
