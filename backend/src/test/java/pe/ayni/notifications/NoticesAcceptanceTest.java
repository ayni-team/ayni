package pe.ayni.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingCancelled.CancelledBy;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.CreditsExpiring;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.events.ValidationResolved;

/**
 * The remaining notices from the events that cause them, over SMTP to GreenMail and against a real
 * PostgreSQL: one test per scenario of {@code features/notifications-notices.feature}, plus the
 * confirmation of a booking of {@code US03-book-a-block.feature}, each with the scenario's name.
 *
 * <p>The events are the real ones, published inside a transaction the way their modules publish
 * them, so the listeners of every module that reacts to them run too. Identity is the only module
 * stubbed: it answers the address of each person, which is what the notices ask of it. The people
 * are signed in with real tokens, so the last scenario reads the notices through the inbox.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.mail.host=localhost", "spring.mail.port=3025"})
class NoticesAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = NotificationsTestDatabase.INSTANCE;

  /** A fresh inbox for every test, listening where {@code spring.mail.port} points. */
  @RegisterExtension
  static final GreenMailExtension INBOX = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final String ANAS_EMAIL = "ana@notices.test";
  private static final String BRUNOS_EMAIL = "bruno@notices.test";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transactions;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @MockitoBean private IdentityApi identity;

  private String university;
  private Person ana;
  private Person bruno;

  @BeforeEach
  void aUniversityWithAStudentAndATutor() {
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    university = people.aUniversity("N");
    ana = people.aStudent(university);
    bruno = people.aStudent(university);
    when(identity.requireUser(ana.id())).thenReturn(userView(ana, ANAS_EMAIL));
    when(identity.requireUser(bruno.id())).thenReturn(userView(bruno, BRUNOS_EMAIL));
    when(identity.requireTenant(university))
        .thenReturn(
            new TenantView(university, "University", "America/Lima", new BigDecimal("13.00"), true));
  }

  private UserView userView(Person person, String email) {
    return new UserView(
        person.id(),
        university,
        UserRole.STUDENT,
        email,
        "U" + person.id().toString().substring(0, 8),
        "Name",
        "Software Engineering",
        "2026-2",
        null);
  }

  private void publish(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  private BookingConfirmed bookingConfirmed(UUID bookingId) {
    Instant startsAt = Instant.parse("2026-10-21T14:00:00Z");
    return new BookingConfirmed(
        university,
        bookingId,
        ana.id(),
        bruno.id(),
        UUID.randomUUID(),
        startsAt,
        startsAt.plus(2, ChronoUnit.HOURS),
        List.of(UUID.randomUUID(), UUID.randomUUID()),
        Credits.of(2),
        Instant.now());
  }

  private BookingCancelled bookingCancelled(UUID bookingId, CancelledBy by, boolean late) {
    return new BookingCancelled(
        university, bookingId, ana.id(), bruno.id(), List.of(), by, late, Instant.now());
  }

  private SessionCompleted sessionCompleted(UUID sessionId) {
    return new SessionCompleted(
        university,
        sessionId,
        UUID.randomUUID(),
        bruno.id(),
        ana.id(),
        UUID.randomUUID(),
        Credits.of(2),
        Instant.now());
  }

  private SessionUnverified sessionUnverified(UUID sessionId) {
    return new SessionUnverified(
        university,
        sessionId,
        UUID.randomUUID(),
        bruno.id(),
        ana.id(),
        List.of(bruno.id()),
        Instant.now());
  }

  private CreditsExpiring creditsExpiring() {
    return new CreditsExpiring(
        university,
        ana.id(),
        Credits.of(5),
        Instant.parse("2026-10-27T02:00:00Z"),
        Instant.parse("2026-10-20T08:00:00Z"));
  }

  private RecognitionResolved recognitionResolved(UUID requestId) {
    return new RecognitionResolved(
        university, requestId, ana.id(), true, "Cumple las horas pedidas", Instant.now());
  }

  private ValidationResolved validationResolved(UUID validationId) {
    return new ValidationResolved(
        university,
        validationId,
        bruno.id(),
        UUID.randomUUID(),
        false,
        "El portafolio no muestra la herramienta",
        Instant.now());
  }

  private List<MimeMessage> emailsTo(String address) {
    return Arrays.stream(INBOX.getReceivedMessages())
        .filter(
            message -> {
              try {
                return message.getAllRecipients()[0].toString().equals(address);
              } catch (Exception unreadable) {
                return false;
              }
            })
        .toList();
  }

  private String bodyOf(MimeMessage email) throws Exception {
    return (String) email.getContent();
  }

  private List<Map<String, Object>> notices(String kind, Person recipient) {
    return jdbc.queryForList(
        """
        select recipient_email, payload::text as payload, sent_at, failed_reason
        from notifications.notifications
        where tenant_id = ? and kind = ? and recipient_id = ?
        """,
        university,
        kind,
        recipient.id());
  }

  @Test
  @DisplayName("Receiving the booking confirmation")
  void receivingTheBookingConfirmation() throws Exception {

    publish(bookingConfirmed(UUID.randomUUID()));

    MimeMessage toAna = emailsTo(ANAS_EMAIL).get(0);
    assertThat(toAna.getSubject()).isEqualTo("Tu tutoría en Ayni quedó confirmada");
    assertThat(bodyOf(toAna))
        .contains("miércoles 21 de octubre de 2026, de 09:00 a 11:00 (America/Lima)");
    MimeMessage toBruno = emailsTo(BRUNOS_EMAIL).get(0);
    assertThat(toBruno.getSubject()).isEqualTo("Tienes una nueva tutoría en Ayni");

    Map<String, Object> anas = notices("BOOKING_CONFIRMED", ana).get(0);
    assertThat(anas.get("sent_at")).isNotNull();
    assertThat((String) anas.get("payload"))
        .contains("\"role\": \"STUDENT\"", "\"startsAt\": \"2026-10-21T14:00:00Z\"");
    assertThat((String) notices("BOOKING_CONFIRMED", bruno).get(0).get("payload"))
        .contains("\"role\": \"TUTOR\"");
  }

  @Test
  @DisplayName("A cancellation by the student tells the tutor")
  void aCancellationByTheStudentTellsTheTutor() throws Exception {

    publish(bookingCancelled(UUID.randomUUID(), CancelledBy.STUDENT, true));

    assertThat(emailsTo(BRUNOS_EMAIL)).hasSize(1);
    assertThat(emailsTo(BRUNOS_EMAIL).get(0).getSubject())
        .isEqualTo("Un estudiante canceló su tutoría en Ayni");
    assertThat(emailsTo(ANAS_EMAIL)).as("whoever cancelled already knows").isEmpty();
    assertThat((String) notices("BOOKING_CANCELLED", bruno).get(0).get("payload"))
        .contains("\"cancelledBy\": \"STUDENT\"", "\"late\": \"true\"");
    assertThat(notices("BOOKING_CANCELLED", ana)).isEmpty();
  }

  @Test
  @DisplayName("A cancellation by the tutor tells the student")
  void aCancellationByTheTutorTellsTheStudent() throws Exception {

    publish(bookingCancelled(UUID.randomUUID(), CancelledBy.TUTOR, false));

    assertThat(emailsTo(ANAS_EMAIL)).hasSize(1);
    assertThat(bodyOf(emailsTo(ANAS_EMAIL).get(0))).contains("se te devolvieron");
    assertThat(emailsTo(BRUNOS_EMAIL)).isEmpty();
    assertThat((String) notices("BOOKING_CANCELLED", ana).get(0).get("payload"))
        .contains("\"cancelledBy\": \"TUTOR\"", "\"late\": \"false\"");
  }

  @Test
  @DisplayName("Expiring credits tell the student")
  void expiringCreditsTellTheStudent() throws Exception {

    publish(creditsExpiring());

    MimeMessage email = emailsTo(ANAS_EMAIL).get(0);
    assertThat(email.getSubject()).isEqualTo("Tus créditos de Ayni están por vencer");
    assertThat(bodyOf(email)).contains("5 créditos de tu saldo vencen el lunes 26 de octubre");
    assertThat((String) notices("CREDITS_EXPIRING", ana).get(0).get("payload"))
        .contains("\"amount\": \"5\"", "\"expiresAt\": \"2026-10-27T02:00:00Z\"");
  }

  @Test
  @DisplayName("A completed session tells both and invites the student to rate it")
  void aCompletedSessionTellsBoth() throws Exception {
    UUID session = UUID.randomUUID();

    publish(sessionCompleted(session));

    assertThat(bodyOf(emailsTo(ANAS_EMAIL).get(0))).contains("califícala");
    assertThat(bodyOf(emailsTo(BRUNOS_EMAIL).get(0))).contains("Ganaste 2 créditos");
    assertThat((String) notices("SESSION_CLOSED", ana).get(0).get("payload"))
        .contains(
            "\"sessionId\": \"" + session + "\"",
            "\"outcome\": \"COMPLETED\"",
            "\"role\": \"STUDENT\"");
    assertThat((String) notices("SESSION_CLOSED", bruno).get(0).get("payload"))
        .contains("\"role\": \"TUTOR\"", "\"creditsEarned\": \"2\"");
  }

  @Test
  @DisplayName("An unverified session tells both that nobody earns and the student is refunded")
  void anUnverifiedSessionTellsBoth() throws Exception {

    publish(sessionUnverified(UUID.randomUUID()));

    assertThat(bodyOf(emailsTo(ANAS_EMAIL).get(0)))
        .contains("se te devolvieron")
        .doesNotContain("califícala");
    assertThat(bodyOf(emailsTo(BRUNOS_EMAIL).get(0))).contains("No se acreditaron créditos");
    assertThat((String) notices("SESSION_CLOSED", ana).get(0).get("payload"))
        .contains("\"outcome\": \"UNVERIFIED\"");
    assertThat((String) notices("SESSION_CLOSED", bruno).get(0).get("payload"))
        .contains("\"outcome\": \"UNVERIFIED\"", "\"creditsEarned\": \"0\"");
  }

  @Test
  @DisplayName("A decision on a recognition request tells the student without its reason")
  void aDecisionOnARecognitionRequestTellsTheStudent() throws Exception {
    UUID request = UUID.randomUUID();

    publish(recognitionResolved(request));

    MimeMessage email = emailsTo(ANAS_EMAIL).get(0);
    assertThat(email.getSubject()).isEqualTo("Tu solicitud de reconocimiento fue aprobada");
    Map<String, Object> notice = notices("REQUEST_RESOLVED", ana).get(0);
    assertThat((String) notice.get("payload"))
        .contains("\"requestId\": \"" + request + "\"", "\"approved\": \"true\"")
        .doesNotContain("Cumple las horas pedidas");
    assertThat(bodyOf(email)).doesNotContain("Cumple las horas pedidas");
  }

  @Test
  @DisplayName("A decision on the evidence for a tool tells the tutor without its reason")
  void aDecisionOnTheEvidenceForAToolTellsTheTutor() throws Exception {
    UUID validation = UUID.randomUUID();

    publish(validationResolved(validation));

    MimeMessage email = emailsTo(BRUNOS_EMAIL).get(0);
    assertThat(email.getSubject()).isEqualTo("La evidencia de tu herramienta no fue aprobada");
    Map<String, Object> notice = notices("VALIDATION_RESOLVED", bruno).get(0);
    assertThat((String) notice.get("payload"))
        .contains("\"validationRequestId\": \"" + validation + "\"", "\"approved\": \"false\"")
        .doesNotContain("portafolio");
    assertThat(bodyOf(email)).doesNotContain("portafolio");
  }

  @Test
  @DisplayName("An email the server refuses leaves the notice failed and the event delivered")
  void anEmailTheServerRefusesLeavesTheNoticeFailed() {
    INBOX.stop();

    publish(bookingConfirmed(UUID.randomUUID()));
    publish(sessionCompleted(UUID.randomUUID()));

    for (String kind : List.of("BOOKING_CONFIRMED", "SESSION_CLOSED")) {
      for (Person person : List.of(ana, bruno)) {
        Map<String, Object> notice = notices(kind, person).get(0);
        assertThat(notice.get("sent_at")).isNull();
        assertThat((String) notice.get("failed_reason")).isNotBlank();
      }
    }
  }

  @Test
  @DisplayName("The same event delivered twice tells nobody twice")
  void theSameEventDeliveredTwiceTellsNobodyTwice() {
    BookingConfirmed confirmed = bookingConfirmed(UUID.randomUUID());
    BookingCancelled cancelled = bookingCancelled(UUID.randomUUID(), CancelledBy.TUTOR, false);
    CreditsExpiring expiring = creditsExpiring();
    SessionCompleted completed = sessionCompleted(UUID.randomUUID());
    SessionUnverified unverified = sessionUnverified(UUID.randomUUID());
    RecognitionResolved recognition = recognitionResolved(UUID.randomUUID());
    ValidationResolved validation = validationResolved(UUID.randomUUID());

    for (Object event :
        List.of(confirmed, cancelled, expiring, completed, unverified, recognition, validation)) {
      publish(event);
      publish(event);
    }

    // Ana: confirmed, cancelled, expiring, two closings, recognition. Bruno: confirmed, two
    // closings, validation. Every one of them once.
    assertThat(emailsTo(ANAS_EMAIL)).hasSize(6);
    assertThat(emailsTo(BRUNOS_EMAIL)).hasSize(4);
    Integer anas =
        jdbc.queryForObject(
            "select count(*) from notifications.notifications where tenant_id = ? and recipient_id = ?",
            Integer.class,
            university,
            ana.id());
    Integer brunos =
        jdbc.queryForObject(
            "select count(*) from notifications.notifications where tenant_id = ? and recipient_id = ?",
            Integer.class,
            university,
            bruno.id());
    assertThat(anas).isEqualTo(6);
    assertThat(brunos).isEqualTo(4);
  }

  @Test
  @DisplayName("A person who no longer exists does not stop the other notice")
  void aPersonWhoNoLongerExistsDoesNotStopTheOtherNotice() {
    when(identity.requireUser(bruno.id())).thenThrow(new NoSuchElementException("gone"));

    publish(bookingConfirmed(UUID.randomUUID()));

    assertThat(emailsTo(ANAS_EMAIL)).hasSize(1);
    assertThat(emailsTo(BRUNOS_EMAIL)).isEmpty();
    assertThat(notices("BOOKING_CONFIRMED", ana)).hasSize(1);
    assertThat(notices("BOOKING_CONFIRMED", bruno)).isEmpty();
  }

  @Test
  @DisplayName("The notices appear in the inbox of the person they are for")
  void theNoticesAppearInTheInbox() throws Exception {
    UUID session = UUID.randomUUID();

    publish(sessionCompleted(session));

    mockMvc
        .perform(
            get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + ana.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.unreadCount").value(1))
        .andExpect(jsonPath("$.items[0].kind").value("SESSION_CLOSED"))
        .andExpect(jsonPath("$.items[0].payload.sessionId").value(session.toString()))
        .andExpect(jsonPath("$.items[0].payload.role").value("STUDENT"));
    mockMvc
        .perform(
            get("/api/v1/notifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bruno.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].payload.creditsEarned").value("2"));
  }
}
