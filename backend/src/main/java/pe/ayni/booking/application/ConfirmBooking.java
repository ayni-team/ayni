package pe.ayni.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.StudentNotActive;
import pe.ayni.booking.domain.model.TutorNotBookable;
import pe.ayni.booking.domain.services.RequestedHours;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.SkillsApi;
import pe.ayni.wallet.WalletApi;

/**
 * The confirmation, in the order the backend guide gives it, inside one transaction: the tutor is
 * enabled, the hours are consecutive and free, charge the credits, mark the hours, save the
 * booking, publish {@code BookingConfirmed}. If anything fails, nothing happened.
 *
 * <p>Nothing here catches anything. Wallet's charge runs inside this transaction and a refusal from
 * it marks the transaction for rollback; catching it here and carrying on would end in a commit
 * that fails with {@code UnexpectedRollbackException}. Refusals leave this class as they are, the
 * transaction rolls back whole, and {@link BookHoursUseCase} deals with the aftermath outside it.
 */
@Service
class ConfirmBooking {

  private final HourBlockRepository blocks;
  private final BookingRepository bookings;
  private final IdentityApi identity;
  private final SkillsApi skills;
  private final WalletApi wallet;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  ConfirmBooking(
      HourBlockRepository blocks,
      BookingRepository bookings,
      IdentityApi identity,
      SkillsApi skills,
      WalletApi wallet,
      ApplicationEventPublisher events,
      Clock clock) {
    this.blocks = blocks;
    this.bookings = bookings;
    this.identity = identity;
    this.skills = skills;
    this.wallet = wallet;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  ConfirmedBooking confirm(
      UUID studentId,
      UUID tutorId,
      UUID catalogItemId,
      Instant start,
      int hours,
      String needDescription) {

    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(needDescription, "needDescription must not be null");

    String tenantId = TenantContext.require();
    // One instant for the whole confirmation: every hold is judged against the same now.
    Instant now = clock.instant();

    if (studentId.equals(tutorId)) {
      throw new BookingRuleViolation("A student cannot book their own hours");
    }
    if (!identity.isActive(studentId)) {
      throw new StudentNotActive();
    }

    // 1. The tutor is enabled. The subject has to be one this university can see, the tutor an
    // active member of it, and enabled for that subject here: a foreign key proves none of that.
    skills.requireItem(catalogItemId);
    if (!identity.isActive(tutorId) || !skills.isTutorEnabledFor(tutorId, catalogItemId)) {
      throw new TutorNotBookable();
    }

    // 2. The hours are consecutive and free: found in one query within this university, and held
    // by this student with a hold that has not run out.
    RequestedHours requested =
        RequestedHours.of(
            blocks.findWithin(tenantId, tutorId, start, RequestedHours.endOf(start, hours)),
            start,
            hours);
    for (HourBlock block : requested.blocks()) {
      block.checkBookableBy(studentId, now);
    }

    Booking booking =
        Booking.confirm(
            UUID.randomUUID(),
            tenantId,
            studentId,
            tutorId,
            catalogItemId,
            requested.startsAt(),
            requested.endsAt(),
            requested.hours(),
            needDescription,
            now);

    // 3. Charge the credits, closest to expiring first. Wallet decides the order; the charge rolls
    // back with this transaction if anything below fails.
    wallet.charge(studentId, booking.price(), booking.getId());

    // 4. Mark the hours. Flushed here so that the version check happens now: if another student
    // took one of these hours since they were read, this is where it shows.
    for (HourBlock block : requested.blocks()) {
      block.book(booking.getId(), studentId, now);
    }
    blocks.flush();

    // 5. Save the booking.
    bookings.save(booking);

    // 6. Publish. Listeners run after the commit, so a booking that rolls back is never announced.
    events.publishEvent(
        new BookingConfirmed(
            tenantId,
            booking.getId(),
            studentId,
            tutorId,
            catalogItemId,
            booking.getStartsAt(),
            booking.getEndsAt(),
            requested.blockIds(),
            booking.price(),
            now));

    return ConfirmedBooking.of(booking, requested.blockIds());
  }
}
