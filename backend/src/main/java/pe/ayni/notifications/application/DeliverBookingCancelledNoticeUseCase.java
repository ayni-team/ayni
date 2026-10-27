package pe.ayni.notifications.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.BookingCancelled;

/**
 * Tells the other party that a booking was cancelled (US05): a cancellation by the student tells
 * the tutor, and one by the tutor or by the system tells the student. Whoever cancelled already
 * knows.
 */
@Service
public class DeliverBookingCancelledNoticeUseCase {

  private final NoticeSender sender;

  DeliverBookingCancelledNoticeUseCase(NoticeSender sender) {
    this.sender = sender;
  }

  public void execute(BookingCancelled cancelled) {
    UUID recipientId =
        cancelled.cancelledBy() == BookingCancelled.CancelledBy.STUDENT
            ? cancelled.tutorId()
            : cancelled.studentId();
    sender.deliver(
        List.of(
            new NoticeToSend(
                cancelled.tenantId(),
                recipientId,
                "booking-cancelled:" + cancelled.bookingId(),
                NotificationKind.BOOKING_CANCELLED,
                Map.of(
                    "bookingId", cancelled.bookingId().toString(),
                    "cancelledBy", cancelled.cancelledBy().name(),
                    "late", Boolean.toString(cancelled.late())),
                address -> BookingCancelledEmail.of(cancelled, address))));
  }
}
