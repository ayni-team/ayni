package pe.ayni.booking.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.booking.application.BookHoursUseCase;
import pe.ayni.booking.application.CancelBookingUseCase;
import pe.ayni.booking.application.ConfirmedBooking;
import pe.ayni.booking.application.HoldHoursUseCase;
import pe.ayni.booking.application.HourBlockHorizon;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.shared.demo.DemoBookings;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.SkillsApi;

/**
 * The tutorings of the demonstration, booked the way a student books them: the hours are held,
 * then confirmed, which charges the credits and creates the session; a cancellation is the
 * student's own.
 *
 * <p>{@code DemoBookingData} books the upcoming ones with it, and the history of the {@code demo}
 * profile the past ones, with the clock moved back to a few days before each.
 */
@Component
@Profile("dev")
class DemoTutorings implements DemoBookings {

  private final HoldHoursUseCase holdHours;
  private final BookHoursUseCase bookHours;
  private final CancelBookingUseCase cancelBooking;
  private final HourBlockHorizon horizon;
  private final HourBlockRepository blocks;
  private final BookingRepository bookings;
  private final SkillsApi skills;
  private final Clock clock;

  DemoTutorings(
      HoldHoursUseCase holdHours,
      BookHoursUseCase bookHours,
      CancelBookingUseCase cancelBooking,
      HourBlockHorizon horizon,
      HourBlockRepository blocks,
      BookingRepository bookings,
      SkillsApi skills,
      Clock clock) {
    this.holdHours = holdHours;
    this.bookHours = bookHours;
    this.cancelBooking = cancelBooking;
    this.horizon = horizon;
    this.blocks = blocks;
    this.bookings = bookings;
    this.skills = skills;
    this.clock = clock;
  }

  @Override
  public BookedTutoring bookFirstOpen(
      UUID studentId, UUID tutorId, Instant earliest, int hours, String needDescription) {
    List<UUID> teaches = skills.enabledSkillsOf(tutorId);
    if (teaches.isEmpty()) {
      throw new NoSuchElementException("The tutor " + tutorId + " teaches nothing");
    }
    // The hours exist up to the horizon of the day the clock reads, as the nightly job leaves them.
    horizon.fillFor(tutorId);

    Instant start = firstOpenStretch(tutorId, earliest, hours);
    // A different course of the tutor's for each booking, so the history is not one course over and
    // over: the hour of the day picks it.
    UUID course = teaches.get(Math.floorMod(start.hashCode(), teaches.size()));

    holdHours.execute(studentId, tutorId, start, hours);
    ConfirmedBooking booked =
        bookHours.execute(studentId, tutorId, course, start, hours, needDescription);
    return new BookedTutoring(booked.id(), booked.startsAt(), booked.endsAt());
  }

  @Override
  public void cancel(UUID userId, UUID bookingId) {
    cancelBooking.execute(userId, bookingId, false);
  }

  @Override
  public boolean anyStartingBefore(Instant instant) {
    return bookings.existsByTenantIdAndStartsAtBefore(TenantContext.require(), instant);
  }

  /** The start of the first {@code hours} consecutive free hours of the tutor from that instant. */
  private Instant firstOpenStretch(UUID tutorId, Instant earliest, int hours) {
    Instant now = clock.instant();
    List<HourBlock> open =
        blocks.findOpenFrom(TenantContext.require(), tutorId, earliest).stream()
            .filter(block -> block.isFreeAt(now))
            .toList();
    for (int first = 0; first + hours <= open.size(); first++) {
      boolean consecutive = true;
      for (int next = first + 1; next < first + hours; next++) {
        if (!open.get(next).getStartsAt().equals(open.get(next - 1).getEndsAt())) {
          consecutive = false;
          break;
        }
      }
      if (consecutive) {
        return open.get(first).getStartsAt();
      }
    }
    throw new NoSuchElementException(
        "The tutor " + tutorId + " has no " + hours + " free hours in a row from " + earliest
            + " (" + Duration.between(now, earliest).toDays() + " days ahead)");
  }
}
