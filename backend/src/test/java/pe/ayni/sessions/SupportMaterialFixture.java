package pe.ayni.sessions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingConfirmed;

/** A confirmed booking with its need, as booking would answer it, for the US07 scenarios. */
final class SupportMaterialFixture {

  static final String UPC = "UPC";

  static final String NEED =
      "Normalización hasta la tercera forma normal antes del examen del viernes. No entiendo las"
          + " dependencias transitivas.";

  final UUID student = UUID.randomUUID();
  final UUID tutor = UUID.randomUUID();
  final UUID bookingId = UUID.randomUUID();
  final Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofDays(2));

  SupportMaterialFixture(BookingApi booking) {
    when(booking.isConfirmed(any())).thenReturn(true);
    when(booking.requireBooking(bookingId))
        .thenReturn(
            new BookingView(
                bookingId,
                student,
                tutor,
                UUID.randomUUID(),
                start,
                start.plus(Duration.ofHours(1)),
                1,
                NEED,
                BookingStatus.CONFIRMED));
  }

  void confirm(TransactionTemplate transactions, ApplicationEventPublisher events) {
    transactions.executeWithoutResult(
        status ->
            events.publishEvent(
                new BookingConfirmed(
                    UPC,
                    bookingId,
                    student,
                    tutor,
                    UUID.randomUUID(),
                    start,
                    start.plus(Duration.ofHours(1)),
                    List.of(UUID.randomUUID()),
                    Credits.of(1),
                    Instant.now())));
  }
}
