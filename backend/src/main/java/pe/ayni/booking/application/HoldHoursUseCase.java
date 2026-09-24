package pe.ayni.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourUnavailable;
import pe.ayni.booking.domain.model.HoursNotOffered;
import pe.ayni.booking.domain.model.StudentNotActive;
import pe.ayni.booking.domain.services.RequestedHours;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Takes the hours a student chose out of circulation for five minutes, so nobody else can take them
 * while the student writes what they need help with.
 *
 * <p>Without it two students can be filling in the same confirmation and one of them loses the
 * booking after having written everything. Two students choosing the same free hour at the same
 * moment is decided by the version column: both read it free, one update finds the version changed
 * and loses.
 */
@Service
public class HoldHoursUseCase {

  private final HourBlockRepository blocks;
  private final IdentityApi identity;
  private final Clock clock;

  HoldHoursUseCase(HourBlockRepository blocks, IdentityApi identity, Clock clock) {
    this.blocks = blocks;
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * @param studentId who holds them, always the person making the request
   * @param start the start of the first hour
   * @param hours how many consecutive hours from there
   * @throws BookingRuleViolation when the student is the tutor or no hours are asked for
   * @throws StudentNotActive when the student cannot book
   * @throws HoursNotOffered when the tutor does not offer those consecutive hours
   * @throws HourUnavailable when one of them has started, is booked, or somebody else holds it
   * @throws org.springframework.dao.OptimisticLockingFailureException when another student took one
   *     of them between this request reading it and writing it
   */
  @Transactional
  public HeldHours execute(UUID studentId, UUID tutorId, Instant start, int hours) {

    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(start, "start must not be null");

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    if (studentId.equals(tutorId)) {
      throw new BookingRuleViolation("A student cannot book their own hours");
    }
    if (!identity.isActive(studentId)) {
      throw new StudentNotActive();
    }

    // One query for the whole stretch, filtered by the university: an hour of another university
    // with the same tutor id and start is not found, so it cannot be held.
    RequestedHours requested =
        RequestedHours.of(
            blocks.findWithin(tenantId, tutorId, start, RequestedHours.endOf(start, hours)),
            start,
            hours);

    for (HourBlock block : requested.blocks()) {
      block.hold(studentId, now);
    }
    // Written here rather than at commit, so a lost race is decided inside this call.
    blocks.flush();

    Instant heldUntil =
        requested.blocks().stream()
            .map(HourBlock::getHeldUntil)
            .min(Comparator.naturalOrder())
            .orElseThrow();

    return new HeldHours(
        tutorId,
        requested.startsAt(),
        requested.endsAt(),
        requested.hours(),
        heldUntil,
        requested.blockIds());
  }
}
