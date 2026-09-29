package pe.ayni.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.PresenceCodeIssued;

/** US54's presence code email without Spring: what it says, what is recorded, and in what order. */
class DeliverPresenceCodeUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-09-30T20:05:00Z");
  private static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID SESSION = UUID.fromString("5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f");
  private static final String CODE = "042917";

  private final List<Email> sent = new ArrayList<>();
  private NotificationLog log;
  private EmailDelivery delivery;
  private IdentityApi identity;
  private DeliverPresenceCodeUseCase useCase;

  @BeforeEach
  void setUp() {
    log = mock(NotificationLog.class);
    when(log.record(any())).thenAnswer(call -> call.getArgument(0));
    delivery = mock(EmailDelivery.class);
    doAnswer(call -> sent.add(call.getArgument(0))).when(delivery).send(any());
    identity = mock(IdentityApi.class);
    when(identity.requireUser(ANA))
        .thenReturn(
            new UserView(
                ANA, "UPC", UserRole.STUDENT, "u202400001@upc.edu.pe", "U202400001",
                "Ana Torres", "Software Engineering", "2026-2", null));
    useCase =
        new DeliverPresenceCodeUseCase(log, delivery, identity, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static PresenceCodeIssued issuedTo(UUID participant) {
    return new PresenceCodeIssued(
        "UPC", SESSION, participant, CODE, NOW.plus(Duration.ofMinutes(15)), NOW);
  }

  @Test
  @DisplayName("the code goes to the participant's institutional address, recorded first")
  void recordsThenSendsThenMarks() {

    UUID id = useCase.execute(issuedTo(ANA));

    InOrder order = inOrder(log, delivery);
    order.verify(log).record(any());
    order.verify(delivery).send(any());
    order.verify(log).markSent(id);
    verify(log, never()).markFailed(any(), anyString());

    Email email = sent.getFirst();
    assertThat(email.to()).isEqualTo("u202400001@upc.edu.pe");
    assertThat(email.subject()).isEqualTo("Tu código de presencia en Ayni").doesNotContain(CODE);
    assertThat(email.body())
        .contains(CODE)
        .contains("en los próximos 15 minutos")
        .contains("no verificada");
  }

  @Test
  @DisplayName("the notice names the session and the expiry, never the code")
  void theNoticeNeverHoldsTheCode() {

    useCase.execute(issuedTo(ANA));

    ArgumentCaptor<Notification> captured = ArgumentCaptor.forClass(Notification.class);
    verify(log).record(captured.capture());
    Notification notice = captured.getValue();
    assertThat(notice.getKind()).isEqualTo(NotificationKind.PRESENCE_CODE);
    assertThat(notice.getTenantId()).isEqualTo("UPC");
    assertThat(notice.getRecipientId()).isEqualTo(ANA);
    assertThat(notice.getRecipientEmail()).isEqualTo("u202400001@upc.edu.pe");
    assertThat(notice.getPayload())
        .containsOnly(
            entry("sessionId", SESSION.toString()),
            entry("expiresAt", NOW.plus(Duration.ofMinutes(15)).toString()));
    assertThat(notice.getPayload().values()).noneMatch(value -> value.contains(CODE));
  }

  @Test
  @DisplayName("a delivery that fails is recorded with its reason and does not throw")
  void aFailedDeliveryIsRecorded() {

    doThrow(new EmailNotDelivered("Connection refused", null)).when(delivery).send(any());

    UUID id = useCase.execute(issuedTo(ANA));

    verify(log).markFailed(id, "Connection refused");
    verify(log, never()).markSent(any());
  }

  @Test
  @DisplayName("a participant identity does not know has no address, and nothing is recorded")
  void anUnknownParticipantIsNotRecorded() {

    UUID nobody = UUID.randomUUID();
    when(identity.requireUser(nobody)).thenThrow(new NoSuchElementException("User not found"));

    assertThatThrownBy(() -> useCase.execute(issuedTo(nobody)))
        .isInstanceOf(NoSuchElementException.class);
    verifyNoInteractions(log, delivery);
  }
}
