package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.application.ListDemoPersonasQuery;
import pe.ayni.identity.application.OpenPersonaSessionUseCase;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/** Without the {@code demo} profile acting as a persona does not exist, for an administrator either. */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class DemoPersonasAbsentTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext context;
    @Autowired private PlatformAdminRepository admins;
    @Autowired private UserSessionRepository sessions;
    @Autowired private AccessTokenGenerator tokens;

    @Test
    @DisplayName("Without the demo profile both routes do not exist")
    void withoutTheDemoProfileBothRoutesDoNotExist() throws Exception {
        UUID adminId = UUID.randomUUID();
        Instant now = Instant.now();
        admins.save(
                new PlatformAdmin(
                        adminId,
                        "admin-" + adminId + "@ayni.test",
                        "Admin",
                        PlatformAdminStatus.ACTIVE,
                        now));
        String administrator = tokens.generate().rawToken();
        sessions.save(
                UserSession.forPlatformAdmin(
                        UUID.randomUUID(),
                        adminId,
                        tokens.hash(administrator),
                        now.plus(Duration.ofDays(1)),
                        now));

        mockMvc
                .perform(
                        get("/api/v1/admin/demo/personas")
                                .header("Authorization", "Bearer " + administrator))
                .andExpect(status().isNotFound());
        mockMvc
                .perform(
                        post("/api/v1/admin/demo/login-as")
                                .header("Authorization", "Bearer " + administrator)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"u202400001@upc.edu.pe\"}"))
                .andExpect(status().isNotFound());

        assertThat(context.getBeanNamesForType(ListDemoPersonasQuery.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OpenPersonaSessionUseCase.class)).isEmpty();
    }
}
