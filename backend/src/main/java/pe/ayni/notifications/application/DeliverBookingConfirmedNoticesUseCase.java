package pe.ayni.notifications.application;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.BookingConfirmed;

/** Tells the student and the tutor that the booking is confirmed (US03). */
@Service
public class DeliverBookingConfirmedNoticesUseCase {

  private final NoticeSender sender;

  DeliverBookingConfirmedNoticesUseCase(NoticeSender sender) {
    this.sender = sender;
  }

  public void execute(BookingConfirmed booking) {
    ZoneId zone = sender.universityZone(booking.tenantId());
    sender.deliver(
        List.of(
            noticeTo(booking, booking.studentId(), Participant.STUDENT, zone),
            noticeTo(booking, booking.tutorId(), Participant.TUTOR, zone)));
  }

  private static NoticeToSend noticeTo(
      BookingConfirmed booking, UUID recipientId, Participant role, ZoneId zone) {
    return new NoticeToSend(
        booking.tenantId(),
        recipientId,
        "booking-confirmed:" + booking.bookingId(),
        NotificationKind.BOOKING_CONFIRMED,
        Map.of(
            "bookingId", booking.bookingId().toString(),
            "startsAt", booking.startsAt().toString(),
            "role", role.name()),
        address -> BookingConfirmedEmail.of(booking, role, zone, address));
  }
}
