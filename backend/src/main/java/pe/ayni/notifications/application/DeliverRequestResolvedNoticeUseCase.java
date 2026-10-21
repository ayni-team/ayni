package pe.ayni.notifications.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.RecognitionResolved;

/**
 * Tells a student that a coordinator decided their recognition request (US29).
 *
 * <p>The notice keeps the request and the outcome. The reason is free text, so it is neither in the
 * payload nor in the email: the student reads it with their requests.
 */
@Service
public class DeliverRequestResolvedNoticeUseCase {

  private final NoticeSender sender;

  DeliverRequestResolvedNoticeUseCase(NoticeSender sender) {
    this.sender = sender;
  }

  public void execute(RecognitionResolved resolved) {
    sender.deliver(
        List.of(
            new NoticeToSend(
                resolved.tenantId(),
                resolved.studentId(),
                "request-resolved:" + resolved.requestId(),
                NotificationKind.REQUEST_RESOLVED,
                Map.of(
                    "requestId", resolved.requestId().toString(),
                    "approved", Boolean.toString(resolved.approved())),
                address ->
                    RequestResolvedEmail.of(resolved.requestId(), resolved.approved(), address))));
  }
}
