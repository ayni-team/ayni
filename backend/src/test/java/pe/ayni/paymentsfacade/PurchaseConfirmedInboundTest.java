package pe.ayni.paymentsfacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.PurchaseConfirmed;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;

/**
 * The payments service delivers a confirmed purchase over HTTP and the wallet credits it, once
 * however many times it arrives (Transactional Outbox on one side, Idempotent Consumer on this one,
 * ADR 0011). Scenarios of {@code features/purchase-confirmed-inbound.feature}, with the same names;
 * wallet and notifications are observed only through their public API and the inbox.
 */
@SpringBootTest(properties = {"spring.mail.host=localhost", "spring.mail.port=3025"})
@AutoConfigureMockMvc
@RecordApplicationEvents
class PurchaseConfirmedInboundTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsFacadeTestDatabase.INSTANCE;

  @RegisterExtension
  static final GreenMailExtension INBOX = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final String UPC = "UPC";
  private static final String EVENTS = "/internal/v1/events/purchase-confirmed";

  private final UUID student = UUID.randomUUID();
  private final UUID purchase = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private WalletApi wallet;
  @Autowired private ApplicationEvents events;
  @MockitoBean private IdentityApi identity;

  @BeforeEach
  void aStudentOfUpc() {
    when(identity.activeTenantCodes()).thenReturn(List.of());
    when(identity.requireUser(student))
        .thenReturn(
            new UserView(
                student,
                UPC,
                UserRole.STUDENT,
                "student@upc.edu.pe",
                "202400001",
                "Test Student",
                "Engineering",
                "2026-2",
                null));
  }

  @Test
  @DisplayName("A confirmed purchase reaching the monolith credits the student immediately")
  void aConfirmedPurchaseReachingTheMonolithCreditsTheStudent() throws Exception {
    deliver(confirmation(2), "test-internal-token").andExpect(status().isAccepted());

    assertThat(available()).isEqualTo(Credits.of(2));
    assertThat(events.stream(PurchaseConfirmed.class).filter(e -> e.purchaseId().equals(purchase)))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.tenantId()).isEqualTo(UPC);
              assertThat(event.studentId()).isEqualTo(student);
              assertThat(event.credits()).isEqualTo(Credits.of(2));
            });
    mockMvc
        .perform(
            get("/api/v1/wallet/movements")
                .param("reason", "PURCHASE")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].referenceType").value("PURCHASE"))
        .andExpect(jsonPath("$.items[0].referenceId").value(purchase.toString()));
    assertThat(INBOX.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage email = INBOX.getReceivedMessages()[0];
    assertThat(email.getAllRecipients()[0].toString()).isEqualTo("student@upc.edu.pe");
    assertThat(email.getSubject()).isEqualTo("Tus créditos ya están disponibles en Ayni");
    assertThat((String) email.getContent())
        .contains("acreditamos 2 créditos")
        .contains(purchase.toString());
  }

  @Test
  @DisplayName("The same confirmation delivered twice credits once")
  void theSameConfirmationDeliveredTwiceCreditsOnce() throws Exception {
    String first = confirmation(3);
    deliver(first, "test-internal-token").andExpect(status().isAccepted());
    assertThat(INBOX.waitForIncomingEmail(5000, 1)).isTrue();

    // The relay's answer was lost, so it sends the same event again; and once more under a new id,
    // as a second outbox row of the same purchase would.
    deliver(first, "test-internal-token").andExpect(status().isAccepted());
    deliver(confirmation(3), "test-internal-token").andExpect(status().isAccepted());

    assertThat(available()).isEqualTo(Credits.of(3));
    assertThat(INBOX.getReceivedMessages()).hasSize(1);
  }

  @Test
  @DisplayName("Purchased credits do not count towards recognition")
  void purchasedCreditsDoNotCountTowardsRecognition() throws Exception {
    deliver(confirmation(3), "test-internal-token").andExpect(status().isAccepted());

    Credits[] earned = new Credits[1];
    TenantContext.runAs(UPC, () -> earned[0] = wallet.earnedTotal(student));
    assertThat(earned[0]).isEqualTo(Credits.ZERO);
    assertThat(available()).isEqualTo(Credits.of(3));
  }

  @Test
  @DisplayName("An event without the internal token is refused")
  void anEventWithoutTheInternalTokenIsRefused() throws Exception {
    mockMvc
        .perform(post(EVENTS).contentType(MediaType.APPLICATION_JSON).content(confirmation(2)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Only the payments service may call this route"));
    deliver(confirmation(2), "a-guess").andExpect(status().isUnauthorized());
    // Neither is a student's session a way in.
    mockMvc
        .perform(
            post(EVENTS)
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmation(2)))
        .andExpect(status().isUnauthorized());

    assertThat(available()).isEqualTo(Credits.ZERO);
  }

  @Test
  @DisplayName("An incomplete event is refused")
  void anIncompleteEventIsRefused() throws Exception {
    deliver(
            """
            {"eventId":"%s","tenantId":"UPC","purchaseId":"%s","studentId":"%s","credits":0,
             "occurredOn":"2026-11-25T12:00:00Z"}"""
                .formatted(UUID.randomUUID(), purchase, student),
            "test-internal-token")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("credits must be at least 1"));
    deliver("{\"tenantId\":\"UPC\"}", "test-internal-token").andExpect(status().isBadRequest());
    deliver("not json", "test-internal-token").andExpect(status().isBadRequest());

    assertThat(available()).isEqualTo(Credits.ZERO);
  }

  private String confirmation(int credits) {
    return """
        {"eventId":"%s","tenantId":"UPC","purchaseId":"%s","studentId":"%s","credits":%d,
         "occurredOn":"%s"}"""
        .formatted(
            UUID.randomUUID(),
            purchase,
            student,
            credits,
            Instant.now().truncatedTo(ChronoUnit.SECONDS));
  }

  private ResultActions deliver(String body, String token) throws Exception {
    return mockMvc.perform(
        post(EVENTS)
            .header("X-Internal-Token", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private Credits available() {
    Credits[] credits = new Credits[1];
    TenantContext.runAs(UPC, () -> credits[0] = wallet.balanceOf(student).available());
    return credits[0];
  }
}
