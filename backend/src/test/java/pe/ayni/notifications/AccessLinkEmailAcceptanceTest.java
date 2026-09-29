package pe.ayni.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.identity.infrastructure.DemoIdentityData;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;

/**
 * US38 from the student's side: the link is asked for, arrives in the inbox, and opens a session.
 *
 * <p>Nothing is mocked. The email goes over SMTP to GreenMail, an SMTP server inside the test, and
 * the test reads the link from the message the way the student would, then confirms it. Identity,
 * notifications and the database are the real ones; the people are the demo ones, created by
 * {@link DemoIdentityData} itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.mail.host=localhost", "spring.mail.port=3025"})
class AccessLinkEmailAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = NotificationsTestDatabase.INSTANCE;

  /** A fresh inbox for every test, listening where {@code spring.mail.port} points. */
  @RegisterExtension
  static final GreenMailExtension INBOX = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final String ANAS_EMAIL = "u202400001@upc.edu.pe";
  private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private ImportAcademicRecordUseCase importAcademicRecord;
  @Autowired private Clock clock;

  @BeforeEach
  void theDemoUniversityAndItsStudents() throws Exception {
    // Idempotent: the first test creates them, the others find them there.
    new DemoIdentityData(tenants, users, importAcademicRecord, clock).run(null);
  }

  private void requestAccess(String email) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/access/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)))
        .andExpect(status().isAccepted());
  }

  /** The one email in the inbox, which the listener has already sent when the request answers. */
  private MimeMessage theEmail() {
    MimeMessage[] received = INBOX.getReceivedMessages();
    assertThat(received).hasSize(1);
    return received[0];
  }

  private Map<String, Object> lastNoticeTo(String email) {
    return jdbc.queryForMap(
        """
        select tenant_id, recipient_id, kind, payload::text as payload, sent_at, failed_reason
        from notifications.notifications
        where recipient_email = ?
        order by created_at desc
        limit 1
        """,
        email);
  }

  @Test
  @DisplayName("The sign in link arrives by email and opens a session")
  void theSignInLinkArrivesAndOpensASession() throws Exception {

    requestAccess(ANAS_EMAIL);

    MimeMessage email = theEmail();
    assertThat(email.getAllRecipients()[0].toString()).isEqualTo(ANAS_EMAIL);
    assertThat(email.getSubject()).isEqualTo("Tu enlace para ingresar a Ayni");
    String body = (String) email.getContent();
    assertThat(body).contains("funciona una sola vez").contains("vence en 10 minutos");
    Matcher token = TOKEN.matcher(body);
    assertThat(token.find()).as("the email carries the link").isTrue();

    // The student opens the link from the email, and is in.
    mockMvc
        .perform(
            post("/api/v1/access/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token.group(1))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(ANA.toString()))
        .andExpect(jsonPath("$.tenantId").value("UPC"));

    Map<String, Object> notice = lastNoticeTo(ANAS_EMAIL);
    assertThat(notice.get("kind")).isEqualTo("ACCESS_LINK");
    assertThat(notice.get("tenant_id")).isEqualTo("UPC");
    assertThat(notice.get("sent_at")).isNotNull();
    assertThat(notice.get("failed_reason")).isNull();
    // The notice remembers what the link was for, never the link.
    assertThat((String) notice.get("payload"))
        .contains("\"purpose\": \"LOGIN\"")
        .doesNotContain(token.group(1))
        .doesNotContain("token=");
  }

  @Test
  @DisplayName("A new student receives an activation email")
  void aNewStudentReceivesAnActivationEmail() throws Exception {

    String newcomer = "u2099" + UUID.randomUUID().toString().substring(0, 5) + "@upc.edu.pe";

    requestAccess(newcomer);

    MimeMessage email = theEmail();
    assertThat(email.getSubject()).isEqualTo("Activa tu cuenta de Ayni");
    assertThat((String) email.getContent()).contains("activar tu cuenta").containsPattern(TOKEN);

    Map<String, Object> notice = lastNoticeTo(newcomer);
    assertThat(notice.get("recipient_id")).as("nobody has an account yet").isNull();
    assertThat((String) notice.get("payload")).contains("\"purpose\": \"ACTIVATION\"");
    assertThat(notice.get("sent_at")).isNotNull();
  }

  @Test
  @DisplayName("An email that cannot be delivered is recorded as failed")
  void anUndeliverableEmailIsRecordedAsFailed() throws Exception {

    INBOX.stop();

    // The student is still told to check their email: asking again issues a new link.
    requestAccess(ANAS_EMAIL);

    Map<String, Object> notice = lastNoticeTo(ANAS_EMAIL);
    assertThat(notice.get("sent_at")).isNull();
    assertThat((String) notice.get("failed_reason")).isNotBlank();
  }
}
