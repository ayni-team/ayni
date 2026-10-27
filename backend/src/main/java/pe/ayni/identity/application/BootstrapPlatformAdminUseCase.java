package pe.ayni.identity.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;

/**
 * Gives an empty installation its first platform administrator, the one person who can register
 * the first university (TS10).
 *
 * <p>Nothing else can: registering a university and inviting its coordinators are administrator
 * routes, and outside the {@code dev} profile no administrator exists. The address comes from the
 * deployment's configuration; the administrator signs in by link like everybody else.
 *
 * <p>It is safe to run on every start. When an administrator with that address exists, whatever
 * its status, nothing changes: a disabled one stays disabled, since disabling is a decision a
 * restart must not undo.
 */
@Service
public class BootstrapPlatformAdminUseCase {

    private final PlatformAdminRepository admins;
    private final Clock clock;

    BootstrapPlatformAdminUseCase(PlatformAdminRepository admins, Clock clock) {
        this.admins = admins;
        this.clock = clock;
    }

    /** Whether the administrator was created now; false when one with that address was there. */
    @Transactional
    public boolean execute(String email, String fullName) {
        PlatformAdmin admin = PlatformAdmin.first(UUID.randomUUID(), email, fullName, clock.instant());
        if (admins.findByEmailIgnoreCase(admin.getEmail()).isPresent()) {
            return false;
        }
        admins.save(admin);
        return true;
    }
}
