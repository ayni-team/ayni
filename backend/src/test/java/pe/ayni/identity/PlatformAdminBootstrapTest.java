package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.StandaloneStart;
import pe.ayni.identity.application.BootstrapPlatformAdminUseCase;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.infrastructure.PlatformAdminBootstrap;

/**
 * The first platform administrator of an installation, created at startup from {@code
 * ayni.bootstrap.admin-email}, against a real PostgreSQL. One test per scenario of {@code
 * features/production-packaging.feature} that names this class, with the same display name.
 *
 * <p>The context of this class starts with the address set, which is the first start. A second
 * start is the same runner run again over the same database, which is what a restart does.
 */
@SpringBootTest(
        properties = {
            "ayni.bootstrap.admin-email=" + PlatformAdminBootstrapTest.ADDRESS,
            "ayni.bootstrap.admin-full-name=" + PlatformAdminBootstrapTest.NAME
        })
class PlatformAdminBootstrapTest {

    static final String ADDRESS = "First.Admin@Bootstrap.Ayni.Test";
    static final String NAME = "Rosa Quispe";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformAdminBootstrap bootstrap;
    @Autowired private BootstrapPlatformAdminUseCase useCase;

    private int administratorsWith(String email) {
        return jdbc.queryForObject(
                "select count(*) from identity.platform_admins where lower(email) = lower(?)",
                Integer.class,
                email);
    }

    private int administrators() {
        return jdbc.queryForObject("select count(*) from identity.platform_admins", Integer.class);
    }

    @Test
    @DisplayName("The first administrator is created once")
    void theFirstAdministratorIsCreatedOnce() {
        Map<String, Object> admin =
                jdbc.queryForMap(
                        "select email, full_name, status from identity.platform_admins where email = ?",
                        ADDRESS.toLowerCase());

        assertThat(admin.get("email")).isEqualTo("first.admin@bootstrap.ayni.test");
        assertThat(admin.get("full_name")).isEqualTo(NAME);
        assertThat(admin.get("status")).isEqualTo("ACTIVE");
        assertThat(administratorsWith(ADDRESS)).isEqualTo(1);
    }

    @Test
    @DisplayName("A second start does not duplicate the administrator")
    void aSecondStartDoesNotDuplicateTheAdministrator() throws Exception {
        bootstrap.run(null);
        new PlatformAdminBootstrap(useCase, "  first.admin@BOOTSTRAP.ayni.test ", "Somebody Else")
                .run(null);

        assertThat(administratorsWith(ADDRESS)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select full_name from identity.platform_admins where email = ?",
                                String.class,
                                ADDRESS.toLowerCase()))
                .isEqualTo(NAME);
    }

    @Test
    @DisplayName("Without the address nothing is created")
    void withoutTheAddressNothingIsCreated() throws Exception {
        int before = administrators();

        new PlatformAdminBootstrap(useCase, "", NAME).run(null);
        new PlatformAdminBootstrap(useCase, "   ", NAME).run(null);
        new PlatformAdminBootstrap(useCase, null, NAME).run(null);

        assertThat(administrators()).isEqualTo(before);
    }

    @Test
    @DisplayName("A malformed address stops the startup")
    void aMalformedAddressStopsTheStartup() {
        int before = administrators();

        // A whole start of the application over the same database, which must fail and leave
        // nothing behind.
        assertThatThrownBy(
                        () ->
                                StandaloneStart.run(
                                                "--server.port=0",
                                                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                                                "--spring.datasource.username=" + POSTGRES.getUsername(),
                                                "--spring.datasource.password=" + POSTGRES.getPassword(),
                                                "--ayni.bootstrap.admin-email=not-an-address")
                                        .close())
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessage("Email is not valid");

        assertThat(administrators()).isEqualTo(before);
    }
}
