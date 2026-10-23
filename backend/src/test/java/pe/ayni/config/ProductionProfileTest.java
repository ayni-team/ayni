package pe.ayni.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.RequiredEnvironment.MissingEnvironmentException;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * The {@code prod} profile, configured only with the variables the Production section of the
 * README documents, over a PostgreSQL of its own that starts empty: no university, no
 * administrator, nothing but what the migrations create. One test per scenario of {@code
 * features/production-packaging.feature} that names this class, with the same display name.
 *
 * <p>The variables reach the application as properties of the same names, which is how Spring
 * reads an environment variable. The application's role and its password are the ones {@code
 * ApplicationRoleTestConnection} connects with in every test; Flyway migrates as the container's
 * user, its owner.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProductionProfileTest {

  /** Empty when the class starts: the scenarios are about an installation that has nothing yet. */
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @RegisterExtension
  static final GreenMailExtension INBOX = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final String FIRST_ADMINISTRATOR = "first.admin@ayni.example.test";
  private static final String WEB_APPLICATION = "https://ayni.example.test";
  private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

  /** The required variables, plus the optional ones this installation sets. */
  private static Map<String, String> documentedVariables() {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("AYNI_DB_URL", POSTGRES.getJdbcUrl());
    variables.put("AYNI_DB_USER", POSTGRES.getUsername());
    variables.put("AYNI_DB_PASSWORD", POSTGRES.getPassword());
    variables.put("AYNI_DB_APP_USER", ApplicationRoleTestConnection.APPLICATION_ROLE);
    variables.put("AYNI_DB_APP_PASSWORD", ApplicationRoleTestConnection.APPLICATION_ROLE);
    variables.put("AYNI_JITSI_JWT_SECRET", "a-secret-for-this-test-only-of-more-than-32-chars");
    variables.put("AYNI_IDENTITY_ACCESS_LINK_BASE_URL", WEB_APPLICATION + "/access/confirm");
    variables.put("AYNI_BOOTSTRAP_ADMIN_EMAIL", FIRST_ADMINISTRATOR);
    variables.put("AYNI_ALLOWED_ORIGINS", WEB_APPLICATION);
    variables.put("AYNI_MAIL_HOST", "localhost");
    variables.put("AYNI_MAIL_PORT", String.valueOf(ServerSetupTest.SMTP.getPort()));
    return variables;
  }

  @DynamicPropertySource
  static void theDocumentedVariables(DynamicPropertyRegistry registry) {
    documentedVariables().forEach((name, value) -> registry.add(name, () -> value));
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private HealthEndpoint health;
  @Autowired private PlatformAdminRepository admins;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  /** A session of another administrator, made directly, for the routes that need one. */
  private String anAdministratorSession() {
    Instant now = Instant.now();
    UUID adminId = UUID.randomUUID();
    admins.save(
        new PlatformAdmin(
            adminId, "other-" + adminId + "@ayni.example.test", "Other", PlatformAdminStatus.ACTIVE, now));
    String token = tokens.generate().rawToken();
    userSessions.save(
        UserSession.forPlatformAdmin(
            UUID.randomUUID(), adminId, tokens.hash(token), now.plus(Duration.ofHours(1)), now));
    return token;
  }

  @Test
  @DisplayName("The prod profile starts with only the documented variables")
  void theProdProfileStartsWithOnlyTheDocumentedVariables() throws Exception {
    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));

    // What each one is made of, as the endpoint knows it: readiness is the database and the
    // application's own state, and no group nor the whole health asks the mail server.
    assertThat(((CompositeHealth) health.healthForPath("readiness")).getComponents())
        .containsOnlyKeys("readinessState", "db");
    assertThat(((CompositeHealth) health.health()).getComponents()).doesNotContainKey("mail");

    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());

    mockMvc
        .perform(get("/actuator/info").header("Authorization", "Bearer " + anAdministratorSession()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.build.version").isNotEmpty())
        .andExpect(jsonPath("$.build.artifact").value("ayni-backend"));
  }

  @Test
  @DisplayName("A missing required variable stops the startup and names it")
  void aMissingRequiredVariableStopsTheStartupAndNamesIt() {
    List<String> required =
        List.of(
            "AYNI_DB_URL",
            "AYNI_DB_USER",
            "AYNI_DB_PASSWORD",
            "AYNI_DB_APP_USER",
            "AYNI_DB_APP_PASSWORD",
            "AYNI_JITSI_JWT_SECRET",
            "AYNI_IDENTITY_ACCESS_LINK_BASE_URL");

    for (String missing : required) {
      List<String> arguments = new ArrayList<>(List.of("--spring.profiles.active=prod", "--server.port=0"));
      documentedVariables()
          .forEach(
              (name, value) ->
                  // Blank is how an unset variable reaches the application from most deployment
                  // files, and a command line argument wins over a variable of the machine.
                  arguments.add("--" + name + "=" + (name.equals(missing) ? "" : value)));

      assertThatThrownBy(() -> StandaloneStart.run(arguments.toArray(String[]::new)).close())
          .as("starting without %s", missing)
          .isInstanceOf(MissingEnvironmentException.class)
          .hasMessageStartingWith("Missing required environment variables: " + missing + ". ");
    }
  }

  @Test
  @DisplayName("An empty installation creates its first university")
  void anEmptyInstallationCreatesItsFirstUniversity() throws Exception {
    assertThat(jdbc.queryForObject("select count(*) from identity.tenants", Integer.class)).isZero();

    mockMvc
        .perform(
            post("/api/v1/access/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(FIRST_ADMINISTRATOR)))
        .andExpect(status().isAccepted());

    MimeMessage[] received = INBOX.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(FIRST_ADMINISTRATOR);
    String body = (String) received[0].getContent();
    assertThat(body).contains(WEB_APPLICATION + "/access/confirm?token=");
    Matcher token = TOKEN.matcher(body);
    assertThat(token.find()).as("the email carries the link").isTrue();

    String confirmed =
        mockMvc
            .perform(
                post("/api/v1/access/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\": \"%s\"}".formatted(token.group(1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("PLATFORM_ADMIN"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String session = JsonPath.read(confirmed, "$.sessionToken");

    mockMvc
        .perform(
            post("/api/v1/admin/universities")
                .header("Authorization", "Bearer " + session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "FIRST",
                      "name": "First University",
                      "primaryColor": "#003A70",
                      "secondaryColor": "#FFFFFF",
                      "emailDomains": ["first.edu.pe"],
                      "minimumTeachingGrade": 13,
                      "timezone": "America/Lima",
                      "initialCredits": 3,
                      "initialCreditValidityDays": 180
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("FIRST"));
  }

  @Test
  @DisplayName("The global categories exist in every installation")
  void theGlobalCategoriesExistInEveryInstallation() {
    List<Map<String, Object>> categories =
        jdbc.queryForList("select id::text as id, name from skills.categories order by sort_order");

    assertThat(categories)
        .extracting(row -> row.get("id") + " " + row.get("name"))
        .contains(
            "a0000000-0000-4000-8000-000000000001 Technology",
            "a0000000-0000-4000-8000-000000000002 Languages",
            "a0000000-0000-4000-8000-000000000003 Design");
  }
}
