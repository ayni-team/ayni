package pe.ayni.identity.infrastructure;

import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;

/**
 * The one platform administrator of development (TS03).
 *
 * <p>They sign in by link like everybody else: ask for one with {@value #EMAIL} at {@code POST
 * /api/v1/access/request} and open it from Mailpit. The session they get has no university. Only
 * under the {@code dev} profile; a deployment registers its own administrators.
 *
 * <p>It runs once: a second start finds the row already there and leaves it alone.
 */
@Component
@Profile("dev")
public class DemoPlatformAdminData implements ApplicationRunner {

    static final UUID ADMIN = UUID.fromString("99999999-9999-4999-8999-999999999999");

    static final String EMAIL = "admin@ayni.local";

    private final PlatformAdminRepository admins;
    private final Clock clock;

    public DemoPlatformAdminData(PlatformAdminRepository admins, Clock clock) {
        this.admins = admins;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (admins.findByEmailIgnoreCase(EMAIL).isEmpty()) {
            admins.save(
                    new PlatformAdmin(
                            ADMIN,
                            EMAIL,
                            "Ayni Platform Administrator",
                            PlatformAdminStatus.ACTIVE,
                            clock.instant()));
        }
    }
}
