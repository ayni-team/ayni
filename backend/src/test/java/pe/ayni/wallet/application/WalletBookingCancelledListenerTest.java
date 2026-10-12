package pe.ayni.wallet.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.events.BookingCancelled;

class WalletBookingCancelledListenerTest {

  private static final String UPC = "UPC";

  private final GrantInitialCredits initialCredits = mock(GrantInitialCredits.class);
  private final GrantCredits grant = mock(GrantCredits.class);
  private final RefundBooking refund = mock(RefundBooking.class);
  private final WalletEventListeners listeners = new WalletEventListeners(initialCredits, grant, refund);

  @Test
  @DisplayName("a late cancellation by the student does not issue a wallet refund")
  void lateCancellationByTheStudentDoesNotRefund() {
    BookingCancelled event = event(BookingCancelled.CancelledBy.STUDENT, true);

    listeners.on(event);

    verifyNoInteractions(refund, grant, initialCredits);
  }

  @Test
  @DisplayName("a late cancellation by the tutor asks wallet to refund the booking")
  void lateCancellationByTheTutorRefunds() {
    BookingCancelled event = event(BookingCancelled.CancelledBy.TUTOR, true);

    listeners.on(event);

    verify(refund).refund(event.bookingId());
    verifyNoInteractions(grant);
  }

  @Test
  @DisplayName("a non-late cancellation asks wallet to refund the booking")
  void nonLateCancellationRefunds() {
    UUID bookingId = UUID.randomUUID();

    listeners.on(
        new BookingCancelled(
            UPC,
            bookingId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(UUID.randomUUID()),
            BookingCancelled.CancelledBy.STUDENT,
            false,
            Instant.parse("2026-09-29T12:00:00Z")));

    verify(refund).refund(bookingId);
    verifyNoInteractions(grant);
  }

  private static BookingCancelled event(BookingCancelled.CancelledBy by, boolean late) {
    return new BookingCancelled(
        UPC,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        List.of(UUID.randomUUID()),
        by,
        late,
        Instant.parse("2026-09-29T12:00:00Z"));
  }
}
