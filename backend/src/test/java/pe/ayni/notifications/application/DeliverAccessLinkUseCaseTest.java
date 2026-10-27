package pe.ayni.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.AccessRequested;

/** US38's access link email without Spring: what it says, what is recorded, and in what order. */
class DeliverAccessLinkUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");
  private static final String LINK = "http://localhost:5173/access/confirm?token=raw-token";

  private final List<Email> sent = new ArrayList<>();
  private NotificationLog log;
  private EmailDelivery delivery;
  private DeliverAccessLinkUseCase useCase;

  @BeforeEach
  void setUp() {
    log = mock(NotificationLog.class);
    when(log.record(any())).thenAnswer(call -> call.getArgument(0));
    delivery = mock(EmailDelivery.class);
    doAnswer(call -> sent.add(call.getArgument(0))).when(delivery).send(any());
    useCase = new DeliverAccessLinkUseCase(log, delivery, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static AccessRequested request(String purpose, Duration lasting) {
    return new AccessRequested(
        "UPC", "u202400001@upc.edu.pe", purpose, LINK, NOW.plus(lasting), NOW);
  }

  private Notification recorded() {
    ArgumentCaptor<Notification> notice = ArgumentCaptor.forClass(Notification.class);
    verify(log).record(notice.capture());
    return notice.getValue();
  }

  @Test
  @DisplayName("the notice is recorded before the email leaves, then marked sent")
  void recordsThenSendsThenMarks() {

    UUID id = useCase.execute(request("LOGIN", Duration.ofMinutes(10)));

    InOrder order = inOrder(log, delivery);
    order.verify(log).record(any());
    order.verify(delivery).send(any());
    order.verify(log).markSent(id);
    verify(log, never()).markFailed(any(), anyString());
  }

  @Test
  @DisplayName("the notice says what the link was for and when it expires, never the link")
  void theNoticeNeverHoldsTheLink() {

    useCase.execute(request("LOGIN", Duration.ofMinutes(10)));

    Notification notice = recorded();
    assertThat(notice.getKind()).isEqualTo(NotificationKind.ACCESS_LINK);
    assertThat(notice.getTenantId()).isEqualTo("UPC");
    assertThat(notice.getRecipientEmail()).isEqualTo("u202400001@upc.edu.pe");
    assertThat(notice.getRecipientId()).isNull();
    assertThat(notice.getPayload())
        .containsOnly(
            entry("purpose", "LOGIN"),
            entry(
                "expiresAt", NOW.plus(Duration.ofMinutes(10)).toString()));
    assertThat(notice.getPayload().values()).noneMatch(value -> value.contains("token"));
  }

  @Test
  @DisplayName("a sign in email carries the link, says it works once and when it expires")
  void theSignInEmail() {

    useCase.execute(request("LOGIN", Duration.ofMinutes(10)));

    Email email = sent.getFirst();
    assertThat(email.to()).isEqualTo("u202400001@upc.edu.pe");
    assertThat(email.subject()).isEqualTo("Tu enlace para ingresar a Ayni");
    assertThat(email.body())
        .contains(LINK)
        .contains("funciona una sola vez")
        .contains("vence en 10 minutos")
        .contains("Si no lo pediste, ignora este correo");
  }

  @Test
  @DisplayName("an activation email and an invitation say what they are for")
  void activationAndInvitation() {

    useCase.execute(request("ACTIVATION", Duration.ofMinutes(10)));
    useCase.execute(request("COORDINATOR_INVITE", Duration.ofSeconds(30)));

    assertThat(sent.get(0).subject()).isEqualTo("Activa tu cuenta de Ayni");
    assertThat(sent.get(0).body()).contains("activar tu cuenta").contains(LINK);
    assertThat(sent.get(1).subject()).isEqualTo("Tu invitación como coordinador de Ayni");
    // Half a minute is rounded up, and one minute is singular.
    assertThat(sent.get(1).body()).contains("vence en 1 minuto.");
  }

  @Test
  @DisplayName("a delivery that fails is recorded with its reason and does not throw")
  void aFailedDeliveryIsRecorded() {

    doThrow(new EmailNotDelivered("Connection refused", null)).when(delivery).send(any());

    UUID id = useCase.execute(request("LOGIN", Duration.ofMinutes(10)));

    verify(log).markFailed(id, "Connection refused");
    verify(log, never()).markSent(any());
  }
}
