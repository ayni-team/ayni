package pe.ayni.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.SessionAbandoned;

class DeliverTutorNoShowNoticeTest {

  private static final Instant NOW = Instant.parse("2026-09-30T20:10:00Z");
  private static final String TENANT = "UPC";
  private static final UUID SESSION = UUID.randomUUID();
  private static final UUID BOOKING = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID CATALOG_ITEM = UUID.randomUUID();
  private static final String EMAIL = "student@upc.edu.pe";

  private final NotificationLog log = mock(NotificationLog.class);
  private final EmailDelivery delivery = mock(EmailDelivery.class);
  private final IdentityApi identity = mock(IdentityApi.class);
  private DeliverTutorNoShowNotice useCase;

  @BeforeEach
  void setUp() {
    when(identity.requireUser(STUDENT))
        .thenReturn(
            new UserView(
                STUDENT,
                TENANT,
                UserRole.STUDENT,
                EMAIL,
                "U202400001",
                "Ana Torres",
                "Software Engineering",
                "2026-2",
                null));
    when(log.record(any())).thenAnswer(call -> call.getArgument(0));
    useCase =
        new DeliverTutorNoShowNotice(
            log, delivery, identity, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("records the notice before sending an email to the attending student")
  void deliversNoticeAndEmail() {
    useCase.execute(event(true));

    ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
    verify(log).record(notification.capture());
    Notification recorded = notification.getValue();
    assertThat(recorded.getKind()).isEqualTo(NotificationKind.TUTOR_NO_SHOW);
    assertThat(recorded.getTenantId()).isEqualTo(TENANT);
    assertThat(recorded.getRecipientId()).isEqualTo(STUDENT);
    assertThat(recorded.getRecipientEmail()).isEqualTo(EMAIL);
    assertThat(recorded.getPayload()).isEqualTo(Map.of("sessionId", SESSION.toString()));

    ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
    InOrder order = Mockito.inOrder(log, delivery);
    order.verify(log).record(any());
    order.verify(delivery).send(email.capture());
    order.verify(log).markSent(recorded.getId());
    assertThat(email.getValue().to()).isEqualTo(EMAIL);
    assertThat(email.getValue().subject()).contains("devolvieron tus créditos");
    assertThat(email.getValue().body())
        .contains("diez minutos")
        .contains("devolución automática")
        .contains("No necesitas presentar un reclamo");
  }

  @Test
  @DisplayName("records a failed email delivery without losing the notice")
  void logsFailedDelivery() {
    doThrow(new EmailNotDelivered("SMTP unavailable", null)).when(delivery).send(any());

    useCase.execute(event(true));

    ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
    verify(log).record(notification.capture());
    verify(log).markFailed(notification.getValue().getId(), "SMTP unavailable");
    verify(log, never()).markSent(any());
  }

  @Test
  @DisplayName("does not send a student no-show notice when the student was absent")
  void rejectsNoticeForAbsentStudent() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.execute(event(false)))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(identity, log, delivery);
  }

  private static SessionAbandoned event(boolean studentCheckedIn) {
    return new SessionAbandoned(
        TENANT, SESSION, BOOKING, TUTOR, STUDENT, CATALOG_ITEM, studentCheckedIn, NOW);
  }
}
