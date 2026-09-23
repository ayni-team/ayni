package pe.ayni.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.AvailabilityPause;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.services.BlockGenerator;
import pe.ayni.booking.infrastructure.AvailabilityExceptionRepository;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.booking.infrastructure.AvailabilityPauseRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.skills.SkillsApi;
import pe.ayni.shared.events.HoursGenerated;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Materialises a tutor's availability into bookable hours.
 *
 * <p>{@link BlockGenerator} works out which hours the rules produce; this use case loads what it
 * needs, keeps the whole thing in one transaction, writes only the hours that are not there
 * already, and says what appeared.
 *
 * <p>Running it twice over the same horizon is safe, which matters because availability is
 * regenerated whenever a tutor changes anything and a scheduled job walks the same days again.
 */
@Service
public class GenerateHourBlocksUseCase {

  private final AvailabilityPatternRepository patterns;
  private final AvailabilityExceptionRepository exceptions;
  private final AvailabilityPauseRepository pauses;
  private final HourBlockRepository blocks;
  private final BlockGenerator generator;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final SkillsApi skills;

  GenerateHourBlocksUseCase(
      AvailabilityPatternRepository patterns,
      AvailabilityExceptionRepository exceptions,
      AvailabilityPauseRepository pauses,
      HourBlockRepository blocks,
      BlockGenerator generator,
      ApplicationEventPublisher events,
      Clock clock,
      SkillsApi skills) {
    this.patterns = patterns;
    this.exceptions = exceptions;
    this.pauses = pauses;
    this.blocks = blocks;
    this.generator = generator;
    this.events = events;
    this.clock = clock;
    this.skills = skills;
  }

  /** Generates the hours of a tutor between two dates, both included. */
  @Transactional
  public void execute(UUID tutorId, LocalDate from, LocalDate to, ZoneId zone) {

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    // A tutor without an enabled skill cannot publish bookable tutoring hours.
    if (skills.enabledSkillsOf(tutorId).isEmpty()) {
      return;
    }

    List<AvailabilityPattern> active =
        patterns.findActiveInHorizon(tenantId, tutorId, from, to);
    List<AvailabilityException> deviations =
        exceptions.findWithinHorizon(tenantId, tutorId, from, to);
    List<AvailabilityPause> away = pauses.findOverlappingPauses(tenantId, tutorId, from, to);

    List<HourBlock> generated =
        generator.generate(active, deviations, away, from, to, zone, now);

    if (generated.isEmpty()) {
      return;
    }

    // The whole horizon in instants, from the first moment of the first day to the first moment
    // of the day after the last: half open, like the query it feeds.
    Instant horizonStart = ZonedDateTime.of(from, LocalTime.MIN, zone).toInstant();
    Instant horizonEnd = ZonedDateTime.of(to.plusDays(1), LocalTime.MIN, zone).toInstant();

    Set<Instant> taken =
        blocks.findWithin(tenantId, tutorId, horizonStart, horizonEnd).stream()
            .map(HourBlock::getStartsAt)
            .collect(Collectors.toCollection(HashSet::new));

    // What the database does not have yet. The generator already refuses to describe an hour
    // twice, so this is only about what earlier runs left behind.
    List<HourBlock> fresh = generated.stream().filter(block -> taken.add(block.getStartsAt())).toList();

    if (fresh.isEmpty()) {
      return;
    }

    blocks.saveAll(fresh);

    // Matching listens for this and adds one offer per block and per skill the tutor teaches.
    events.publishEvent(
        new HoursGenerated(
            tenantId,
            tutorId,
            fresh.stream()
                .map(block -> new HoursGenerated.Block(block.getId(), block.getStartsAt()))
                .toList(),
            now));
  }
}
