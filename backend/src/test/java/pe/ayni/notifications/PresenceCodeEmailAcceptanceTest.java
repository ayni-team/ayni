package pe.ayni.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
import pe.ayni.sessions.application.IssuePresenceCodesUseCase;
import pe.ayni.sessions.application.ScheduleSessionUseCase;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US54 from the participants' side: the code arrives in each inbox and confirms presence.
 *
 * <p>Nothing is mocked. Sessions issues the codes through the use case its job calls, notifications
 * emails them over SMTP to GreenMail, and the test reads each code from the email the way the
 * participant would, then types it in. The people are the demo ones, Ana and Bruno, created by
 * {@link DemoIdentityData} itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.mail.host=localhost", "spring.mail.port=3025"})
class PresenceCodeEmailAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = NotificationsTestDatabase.INSTANCE;

  @RegisterExtension
  static final GreenMailExtension INBOX = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final String UPC = "UPC";
  private static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID BRUNO = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final String ANAS_EMAIL = "u202400001@upc.edu.pe";
  private static final String BRUNOS_EMAIL = "u202300002@upc.edu.pe";
  private static final Pattern CODE = Pattern.compile("^\\s+(\\d{6})\\s*$", Pattern.MULTILINE);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private ImportAcademicRecordUseCase importAcademicRecord;
  @Autowired private ScheduleSessionUseCase scheduleSession;
  @Autowired private IssuePresenceCodesUseCase issuePresenceCodes;
  @Autowired private Clock clock;

  @BeforeEach
  void theDemoUniversityAndItsStudents() throws Exception {
    new DemoIdentityData(tenants, users, importAcademicRecord, clock).run(null);
  }

  /**
   * Ana's session with Bruno as her tutor, six minutes into its hour, joined by both.
   *
   * <p>The joins must happen before US09's ten-minute attendance deadline for the session to
   * start; six minutes also makes US54's five-minute presence-code deadline due.
   */
  private UUID aSessionSixMinutesIn() throws Exception {
    Instant start = Instant.now().minus(Duration.ofMinutes(6));
    UUID bookingId = UUID.randomUUID();
    TenantContext.runAs(
        UPC,
        () -> scheduleSession.forBooking(bookingId, ANA, BRUNO, start, start.plus(Duration.ofHours(1))));
    UUID session =
        jdbc.queryForObject(
            "select id from sessions.sessions where tenant_id = ? and booking_id = ?",
            UUID.class,
            UPC,
            bookingId);
    for (UUID participant : List.of(ANA, BRUNO)) {
      mockMvc
          .perform(
              post("/api/v1/sessions/{id}/join", session)
                  .header("X-Tenant-Id", UPC)
                  .header("X-User-Id", participant))
          .andExpect(status().isOk());
    }
    return session;
  }

  private MimeMessage emailTo(String address) {
    return Arrays.stream(INBOX.getReceivedMessages())
        .filter(
            message -> {
              try {
                return message.getAllRecipients()[0].toString().equals(address);
              } catch (Exception unreadable) {
                return false;
              }
            })
        .findFirst()
        .orElseThrow(() -> new AssertionError("No email to " + address));
  }

  private static String codeIn(MimeMessage email) throws Exception {
    Matcher code = CODE.matcher((String) email.getContent());
    assertThat(code.find()).as("the email carries a six digit code").isTrue();
    return code.group(1);
  }

  @Test
  @DisplayName("Each participant receives their code by email and confirms with it")
  void theCodeArrivesAndConfirmsPresence() throws Exception {

    UUID session = aSessionSixMinutesIn();

    TenantContext.runAs(UPC, () -> issuePresenceCodes.issueFor(session));

    assertThat(INBOX.getReceivedMessages()).hasSize(2);
    MimeMessage anas = emailTo(ANAS_EMAIL);
    assertThat(anas.getSubject()).isEqualTo("Tu código de presencia en Ayni");
    assertThat((String) anas.getContent()).contains("en los próximos 15 minutos");
    String anasCode = codeIn(anas);

    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/presence", session)
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", ANA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\"}".formatted(anasCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmedAt").exists());
    assertThat(codeIn(emailTo(BRUNOS_EMAIL))).as("Bruno gets his own").isNotBlank();

    List<Map<String, Object>> notices =
        jdbc.queryForList(
            """
            select recipient_id, recipient_email, kind, payload::text as payload, sent_at
            from notifications.notifications
            where kind = 'PRESENCE_CODE' and payload ->> 'sessionId' = ?
            """,
            session.toString());
    assertThat(notices).hasSize(2);
    assertThat(notices)
        .extracting(notice -> notice.get("recipient_email"))
        .containsExactlyInAnyOrder(ANAS_EMAIL, BRUNOS_EMAIL);
    assertThat(notices).allSatisfy(notice -> assertThat(notice.get("sent_at")).isNotNull());
    // The notice remembers the session and the expiry, never the code.
    assertThat(notices)
        .allSatisfy(notice -> assertThat((String) notice.get("payload")).doesNotContain(anasCode));
  }
}
