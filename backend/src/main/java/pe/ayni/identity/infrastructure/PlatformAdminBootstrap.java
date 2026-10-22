package pe.ayni.identity.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import pe.ayni.identity.application.BootstrapPlatformAdminUseCase;

/**
 * Creates the first platform administrator at startup, in any profile, when the deployment names
 * one in {@code ayni.bootstrap.admin-email} ({@code AYNI_BOOTSTRAP_ADMIN_EMAIL}).
 *
 * <p>Without that address, or with a blank one, it does nothing. With an address that is not one
 * the startup fails, rather than leaving an installation that nobody can administer. The full name
 * is {@code ayni.bootstrap.admin-full-name}, with a generic one by default.
 *
 * <p>The address is never written to the log: the log only says whether an administrator was
 * created.
 */
@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

    static final String DEFAULT_FULL_NAME = "Ayni Platform Administrator";

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final BootstrapPlatformAdminUseCase bootstrap;
    private final String email;
    private final String fullName;

    public PlatformAdminBootstrap(
            BootstrapPlatformAdminUseCase bootstrap,
            @Value("${ayni.bootstrap.admin-email:}") String email,
            @Value("${ayni.bootstrap.admin-full-name:" + DEFAULT_FULL_NAME + "}") String fullName) {
        this.bootstrap = bootstrap;
        this.email = email;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) {
            return;
        }
        String name = fullName == null || fullName.isBlank() ? DEFAULT_FULL_NAME : fullName;
        if (bootstrap.execute(email, name)) {
            log.info("Created the first platform administrator from ayni.bootstrap.admin-email");
        } else {
            log.info("The platform administrator of ayni.bootstrap.admin-email already exists");
        }
    }
}
