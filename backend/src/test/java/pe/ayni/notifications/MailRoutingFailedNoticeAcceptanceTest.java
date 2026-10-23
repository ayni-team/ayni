package pe.ayni.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.identity.infrastructure.DemoIdentityData;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;

/**
 * A real relay that is down, from the student's side: the student is allowed to receive real
 * email, the relay does not answer, and the application records the failure on the notice instead
 * of handing the link to the main server.
 *
 * <p>The whole application runs against a real PostgreSQL. The main server is GreenMail, and the
 * real relay is a port where nobody listens.
 */
@SpringBootTest(
    properties = {
      "spring.mail.host=localhost",
      "spring.mail.port=3025",
      "ayni.notifications.real-relay.allowed-recipients=u202400001@upc.edu.pe",
      "ayni.notifications.real-relay.host=localhost",
      // Nothing listens here.
      "ayni.notifications.real-relay.port=3999"
    })
@AutoConfigureMockMvc
class MailRoutingFailedNoticeAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = NotificationsTestDatabase.INSTANCE;

  @RegisterExtension
  static final GreenMailExtension MAIN_SERVER = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final String ANAS_EMAIL = "u202400001@upc.edu.pe";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private ImportAcademicRecordUseCase importAcademicRecord;
  @Autowired private Clock clock;

  @BeforeEach
  void theDemoUniversityAndItsStudents() throws Exception {
    new DemoIdentityData(tenants, users, importAcademicRecord, clock).run(null);
  }

  @Test
  @DisplayName("A real relay that is down fails the notice and sends nothing to the main server")
  void aRealRelayThatIsDownFailsTheNoticeAndSendsNothingToTheMainServer() throws Exception {

    mockMvc
        .perform(
            post("/api/v1/access/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(ANAS_EMAIL)))
        .andExpect(status().isAccepted());

    Map<String, Object> notice =
        jdbc.queryForMap(
            """
            select sent_at, failed_reason
            from notifications.notifications
            where recipient_email = ?
            order by created_at desc
            limit 1
            """,
            ANAS_EMAIL);
    assertThat(notice.get("sent_at")).isNull();
    assertThat((String) notice.get("failed_reason"))
        .contains("real relay")
        .doesNotContain(ANAS_EMAIL);
    assertThat(MAIN_SERVER.getReceivedMessages()).isEmpty();
  }
}
