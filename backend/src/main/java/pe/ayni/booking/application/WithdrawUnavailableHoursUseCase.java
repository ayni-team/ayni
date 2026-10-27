package pe.ayni.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;
import pe.ayni.booking.domain.services.BlockGenerator;
import pe.ayni.booking.infrastructure.AvailabilityExceptionRepository;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.booking.infrastructure.AvailabilityPauseRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.shared.events.HoursWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Takes out of circulation the hours a tutor's availability no longer gives (US20, US22).
 *
 * <p>Hours are generated weeks ahead, so a pause or a removed date usually arrives after the hours
 * it covers already exist. Those hours have to go, or they stay in the search and a student can
 * book the tutor while they are away.
 *
 * <p>Which hours go is not decided here. {@link BlockGenerator} works out what the tutor's rules
 * produce now, the same way it does when it creates hours, and every future hour that exists and is
 * not among them is withdrawn. Asking the generator rather than writing the rule again for pauses
 * and for exceptions keeps one definition of when a tutor is free; a second one would drift.
 *
 * <p>A booked hour stands, as US19 scenario 4 says: only cancelling undoes a booking, and cancelling
 * refunds. The answer counts those hours so the tutor knows they still have to be there. Hours that
 * have started are left alone, whatever they are.
 *
 * <p>{@link HoursWithdrawn} says which hours left, so matching takes them out of the search.
 * Running it twice changes nothing the second time.
 */
@Service
public class WithdrawUnavailableHoursUseCase {

  private final AvailabilityPatternRepository patterns;
  private final AvailabilityExceptionRepository exceptions;
  private final AvailabilityPauseRepository pauses;
  private final HourBlockRepository blocks;
  private final BlockGenerator generator;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  WithdrawUnavailableHoursUseCase(
      AvailabilityPatternRepository patterns,
      AvailabilityExceptionRepository exceptions,
      AvailabilityPauseRepository pauses,
      HourBlockRepository blocks,
      BlockGenerator generator,
      ApplicationEventPublisher events,
      Clock clock) {
    this.patterns = patterns;
    this.exceptions = exceptions;
    this.pauses = pauses;
    this.blocks = blocks;
    this.generator = generator;
    this.events = events;
    this.clock = clock;
  }

  /** Withdraws the tutor's hours between two dates, both included, that the rules no longer give. */
  @Transactional
  public HoursWithdrawal execute(UUID tutorId, LocalDate from, LocalDate to, ZoneId zone) {

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    Set<Instant> stillGiven =
        generator
            .generate(
                patterns.findActiveInHorizon(tenantId, tutorId, from, to),
                exceptions.findWithinHorizon(tenantId, tutorId, from, to),
                pauses.findOverlappingPauses(tenantId, tutorId, from, to),
                from,
                to,
                zone,
                now)
            .stream()
            .map(HourBlock::getStartsAt)
            .collect(Collectors.toSet());

    // Half open, like the generation: from the first moment of the first day to the first moment
    // of the day after the last.
    Instant rangeStart = ZonedDateTime.of(from, LocalTime.MIN, zone).toInstant();
    Instant rangeEnd = ZonedDateTime.of(to.plusDays(1), LocalTime.MIN, zone).toInstant();

    List<UUID> withdrawn = new ArrayList<>();
    int bookedKept = 0;
    for (HourBlock block : blocks.findWithin(tenantId, tutorId, rangeStart, rangeEnd)) {
      if (block.hasStartedAt(now) || stillGiven.contains(block.getStartsAt())) {
        continue;
      }
      if (block.withdraw()) {
        withdrawn.add(block.getId());
      } else if (block.getStatus() == HourBlockStatus.BOOKED) {
        bookedKept++;
      }
    }

    if (!withdrawn.isEmpty()) {
      // Matching listens for this and removes the offers of these hours, for every course.
      events.publishEvent(new HoursWithdrawn(tenantId, tutorId, List.copyOf(withdrawn), now));
    }
    return new HoursWithdrawal(withdrawn.size(), bookedKept);
  }
}
