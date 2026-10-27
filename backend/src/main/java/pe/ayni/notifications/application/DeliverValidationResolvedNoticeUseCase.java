package pe.ayni.notifications.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.ValidationResolved;

/**
 * Tells a tutor that a coordinator decided the evidence they presented for a global tool (US16,
 * US43). As with a recognition request, the reason stays out of the notice.
 */
@Service
public class DeliverValidationResolvedNoticeUseCase {

  private final NoticeSender sender;

  DeliverValidationResolvedNoticeUseCase(NoticeSender sender) {
    this.sender = sender;
  }

  public void execute(ValidationResolved resolved) {
    sender.deliver(
        List.of(
            new NoticeToSend(
                resolved.tenantId(),
                resolved.tutorId(),
                "validation-resolved:" + resolved.validationRequestId(),
                NotificationKind.VALIDATION_RESOLVED,
                Map.of(
                    "validationRequestId", resolved.validationRequestId().toString(),
                    "catalogItemId", resolved.catalogItemId().toString(),
                    "approved", Boolean.toString(resolved.approved())),
                address ->
                    ValidationResolvedEmail.of(
                        resolved.validationRequestId(), resolved.approved(), address))));
  }
}
