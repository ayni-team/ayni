package pe.ayni.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;

class NoticeSenderTest {

  private static final String ADDRESS = "ana@upc.edu.pe";

  private final NoticeHarness harness = new NoticeHarness();

  private NoticeToSend noticeTo(UUID recipient, String eventKey) {
    return new NoticeToSend(
        NoticeHarness.TENANT,
        recipient,
        eventKey,
        NotificationKind.SESSION_CLOSED,
        Map.of("sessionId", "s-1"),
        address -> new Email(address, "Subject", "Body"));
  }

  @Test
  @DisplayName("writes the notice before the email leaves and marks it sent afterwards")
  void writesBeforeSendingAndMarksSent() {
    UUID ana = harness.personAt(ADDRESS);

    harness.sender.deliver(List.of(noticeTo(ana, "event-1")));

    Notification notice = harness.noticeOf(ana);
    assertThat(notice.getKind()).isEqualTo(NotificationKind.SESSION_CLOSED);
    assertThat(notice.getTenantId()).isEqualTo(NoticeHarness.TENANT);
    assertThat(notice.getRecipientEmail()).isEqualTo(ADDRESS);
    assertThat(notice.getPayload()).isEqualTo(Map.of("sessionId", "s-1"));
    assertThat(notice.getCreatedAt()).isEqualTo(NoticeHarness.NOW);
    InOrder order = inOrder(harness.log, harness.delivery);
    order.verify(harness.log).recordOnce(any());
    order.verify(harness.delivery).send(new Email(ADDRESS, "Subject", "Body"));
    order.verify(harness.log).markSent(notice.getId());
  }

  @Test
  @DisplayName("an email the carrier refuses leaves the notice marked as failed")
  void aRefusedEmailMarksTheNoticeFailed() {
    UUID ana = harness.personAt(ADDRESS);
    doThrow(new EmailNotDelivered("SMTP unavailable", null)).when(harness.delivery).send(any());

    harness.sender.deliver(List.of(noticeTo(ana, "event-1")));

    verify(harness.log).markFailed(harness.noticeOf(ana).getId(), "SMTP unavailable");
    verify(harness.log, never()).markSent(any());
  }

  @Test
  @DisplayName("a recipient identity does not know is skipped and the next one is still told")
  void anUnknownRecipientDoesNotHoldBackTheNext() {
    UUID gone = UUID.randomUUID();
    when(harness.identity.requireUser(gone)).thenThrow(new NoSuchElementException("no user"));
    UUID ana = harness.personAt(ADDRESS);

    harness.sender.deliver(List.of(noticeTo(gone, "event-1"), noticeTo(ana, "event-1")));

    assertThat(harness.recorded).hasSize(1);
    assertThat(harness.sent).extracting(Email::to).containsExactly(ADDRESS);
  }

  @Test
  @DisplayName("a notice already written is not sent again")
  void aNoticeAlreadyWrittenIsNotSentAgain() {
    UUID ana = harness.personAt(ADDRESS);
    doReturn(Optional.empty()).when(harness.log).recordOnce(any());

    harness.sender.deliver(List.of(noticeTo(ana, "event-1")));

    assertThat(harness.sent).isEmpty();
  }

  @Test
  @DisplayName("a twin written at the same moment by another delivery is not sent again")
  void aTwinWrittenAtTheSameMomentIsNotSentAgain() {
    UUID ana = harness.personAt(ADDRESS);
    doThrow(new DataIntegrityViolationException("pk")).when(harness.log).recordOnce(any());

    harness.sender.deliver(List.of(noticeTo(ana, "event-1")));

    assertThat(harness.sent).isEmpty();
  }

  @Test
  @DisplayName("the identifier comes from the event and the recipient, not from chance")
  void theIdentifierIsDerivedFromTheEventAndTheRecipient() {
    UUID ana = harness.personAt(ADDRESS);
    UUID bruno = harness.personAt("bruno@upc.edu.pe");

    harness.sender.deliver(List.of(noticeTo(ana, "event-1"), noticeTo(bruno, "event-1")));
    harness.sender.deliver(List.of(noticeTo(ana, "event-1"), noticeTo(ana, "event-2")));

    List<UUID> ids = harness.recorded.stream().map(Notification::getId).toList();
    assertThat(ids.get(2)).isEqualTo(ids.get(0));
    assertThat(ids.get(1)).isNotEqualTo(ids.get(0));
    assertThat(ids.get(3)).isNotEqualTo(ids.get(0));
  }

  @Test
  @DisplayName("every recipient is tried before a failure is thrown, so the event is delivered again")
  void everyRecipientIsTriedBeforeTheFailureIsThrown() {
    UUID ana = harness.personAt(ADDRESS);
    UUID bruno = harness.personAt("bruno@upc.edu.pe");
    doThrow(new IllegalStateException("database down"))
        .doAnswer(call -> Optional.of(call.getArgument(0)))
        .when(harness.log)
        .recordOnce(any());

    assertThatThrownBy(
            () -> harness.sender.deliver(List.of(noticeTo(ana, "event-1"), noticeTo(bruno, "event-1"))))
        .isInstanceOf(IllegalStateException.class);

    assertThat(harness.sent).extracting(Email::to).containsExactly("bruno@upc.edu.pe");
  }

  @Test
  @DisplayName("the zone of the university comes from identity and falls back to UTC")
  void theZoneComesFromIdentity() {
    assertThat(harness.sender.universityZone(NoticeHarness.TENANT))
        .isEqualTo(ZoneId.of("America/Lima"));
    assertThat(harness.sender.universityZone("UNKNOWN")).isEqualTo(ZoneOffset.UTC);
  }
}
