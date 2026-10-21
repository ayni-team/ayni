package pe.ayni.notifications.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;

/**
 * Tells the student and the tutor how their session ended (US11, US54): verified, with the credits
 * the tutor earned and an invitation for the student to rate it, or unverified, where nobody earns
 * anything and the student is refunded.
 */
@Service
public class DeliverSessionClosedNoticesUseCase {

  private final NoticeSender sender;

  DeliverSessionClosedNoticesUseCase(NoticeSender sender) {
    this.sender = sender;
  }

  public void execute(SessionCompleted completed) {
    sender.deliver(
        List.of(
            noticeTo(
                completed.tenantId(),
                completed.sessionId(),
                completed.studentId(),
                Participant.STUDENT,
                SessionOutcome.COMPLETED,
                0),
            noticeTo(
                completed.tenantId(),
                completed.sessionId(),
                completed.tutorId(),
                Participant.TUTOR,
                SessionOutcome.COMPLETED,
                completed.creditsEarned().amount())));
  }

  public void execute(SessionUnverified unverified) {
    sender.deliver(
        List.of(
            noticeTo(
                unverified.tenantId(),
                unverified.sessionId(),
                unverified.studentId(),
                Participant.STUDENT,
                SessionOutcome.UNVERIFIED,
                0),
            noticeTo(
                unverified.tenantId(),
                unverified.sessionId(),
                unverified.tutorId(),
                Participant.TUTOR,
                SessionOutcome.UNVERIFIED,
                0)));
  }

  private static NoticeToSend noticeTo(
      String tenantId,
      UUID sessionId,
      UUID recipientId,
      Participant role,
      SessionOutcome outcome,
      int creditsEarned) {
    Map<String, String> payload = new HashMap<>();
    payload.put("sessionId", sessionId.toString());
    payload.put("outcome", outcome.name());
    payload.put("role", role.name());
    if (role == Participant.TUTOR) {
      payload.put("creditsEarned", Integer.toString(creditsEarned));
    }
    return new NoticeToSend(
        tenantId,
        recipientId,
        "session-closed:" + sessionId,
        NotificationKind.SESSION_CLOSED,
        payload,
        address -> SessionClosedEmail.of(role, outcome, creditsEarned, address));
  }
}
