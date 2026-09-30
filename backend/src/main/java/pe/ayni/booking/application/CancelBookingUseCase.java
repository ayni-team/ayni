package pe.ayni.booking.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.BookingCancellationConflict;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.CancelledBy;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.ReliabilityIncidentKind;
import pe.ayni.booking.domain.model.TutorReliabilityIncident;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.booking.infrastructure.TutorReliabilityIncidentRepository;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.booking.BookingStatus;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;

/**
 * Cancels a confirmed booking, returning credits for cancellations outside the late window.
 *
 * <p>A late cancellation is not performed until the caller confirms it explicitly. A confirmed
 * cancellation updates the booking and its blocks, refunds synchronously when eligible, records a
 * tutor reliability incident when the tutor cancelled late, and publishes one event for the other
 * modules.
 */
@Service
public class CancelBookingUseCase {

  private static final Duration LATE_CANCELLATION_WINDOW = Duration.ofHours(12);

  private final BookingRepository bookings;
  private final HourBlockRepository blocks;
  private final TutorReliabilityIncidentRepository incidents;
  private final WalletApi wallet;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  CancelBookingUseCase(
      BookingRepository bookings,
      HourBlockRepository blocks,
      TutorReliabilityIncidentRepository incidents,
      WalletApi wallet,
      ApplicationEventPublisher events,
      Clock clock) {
    this.bookings = bookings;
    this.blocks = blocks;
    this.incidents = incidents;
    this.wallet = wallet;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Cancels a booking or returns a warning when explicit confirmation is needed.
   *
   * @param userId the student or tutor making the request
   * @param bookingId the booking to cancel
   * @param confirmLate whether the caller confirmed a cancellation inside the twelve-hour window
   * @return details when late confirmation is required; otherwise empty
   * @throws NoSuchElementException when the booking does not exist or the caller is not a participant
   * @throws BookingCancellationConflict when the booking is no longer cancellable
   */
  @Transactional
  public Optional<LateCancellationConfirmation> execute(
      UUID userId, UUID bookingId, boolean confirmLate) {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(bookingId, "bookingId must not be null");

    String tenantId = TenantContext.require();
    Instant now = clock.instant();
    Booking booking =
        bookings
            .lockByTenantIdAndId(tenantId, bookingId)
            .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));
    CancelledBy cancelledBy = cancelledBy(booking, userId, bookingId);

    if (booking.getStatus() == BookingStatus.CANCELLED) {
      return Optional.empty();
    }
    if (booking.getStatus() != BookingStatus.CONFIRMED) {
      throw new BookingCancellationConflict("Only a confirmed booking can be cancelled");
    }
    if (!now.isBefore(booking.getStartsAt())) {
      throw new BookingCancellationConflict(
          "A tutoring session cannot be cancelled after it has started");
    }

    boolean late =
        Duration.between(now, booking.getStartsAt()).compareTo(LATE_CANCELLATION_WINDOW) < 0;
    if (late && !confirmLate) {
      return Optional.of(
          new LateCancellationConfirmation(bookingId, booking.getStartsAt(), false));
    }

    List<HourBlock> bookingBlocks =
        blocks.findByTenantIdAndBookingIdOrderByStartsAtAsc(tenantId, bookingId);
    if (bookingBlocks.isEmpty()) {
      throw new BookingRuleViolation("The confirmed booking has no booked hour blocks");
    }

    booking.cancel(
        cancelledBy,
        late ? "Late cancellation confirmed; no refund issued" : "Cancelled by participant",
        now);

    for (HourBlock block : bookingBlocks) {
      if (!bookingId.equals(block.getBookingId())) {
        throw new BookingRuleViolation("A booked hour does not belong to this booking");
      }
      if (cancelledBy == CancelledBy.STUDENT) {
        block.returnToAvailability();
      } else {
        block.release();
      }
    }

    if (!late) {
      // Wallet is the only module allowed to write the ledger; this call shares the transaction.
      wallet.refund(bookingId);
    }

    if (late && cancelledBy == CancelledBy.TUTOR) {
      incidents.save(
          new TutorReliabilityIncident(
              UUID.randomUUID(),
              tenantId,
              booking.getTutorId(),
              bookingId,
              ReliabilityIncidentKind.LATE_CANCELLATION,
              now));
    }

    events.publishEvent(
        new BookingCancelled(
            tenantId,
            bookingId,
            booking.getStudentId(),
            booking.getTutorId(),
            bookingBlocks.stream().map(HourBlock::getId).toList(),
            BookingCancelled.CancelledBy.valueOf(cancelledBy.name()),
            late,
            now));

    return Optional.empty();
  }

  private static CancelledBy cancelledBy(Booking booking, UUID userId, UUID bookingId) {
    if (booking.getStudentId().equals(userId)) {
      return CancelledBy.STUDENT;
    }
    if (booking.getTutorId().equals(userId)) {
      return CancelledBy.TUTOR;
    }
    throw new NoSuchElementException("Booking not found: " + bookingId);
  }
}
