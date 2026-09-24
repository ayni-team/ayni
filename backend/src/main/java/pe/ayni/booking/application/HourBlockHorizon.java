package pe.ayni.booking.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * How far ahead a tutor's hours exist: from today to a fixed number of weeks, in the university's
 * own time zone.
 *
 * <p>Two callers keep it filled. Declaring a weekly window generates its hours at once, so they can
 * be found the moment the tutor saves them; and a nightly job moves the horizon forward for every
 * tutor, so the weeks keep coming without anybody saving again. Generation writes only the hours
 * that do not exist yet, so both can run over the same range.
 *
 * <p>The day that counts as today is the university's: a block is shown in its local time, and at
 * eight in the evening in Lima it is already tomorrow in UTC.
 */
@Component
public class HourBlockHorizon {

  private final GenerateHourBlocksUseCase generateHourBlocks;
  private final AvailabilityPatternRepository patterns;
  private final IdentityApi identity;
  private final Clock clock;
  private final int weeks;

  HourBlockHorizon(
      GenerateHourBlocksUseCase generateHourBlocks,
      AvailabilityPatternRepository patterns,
      IdentityApi identity,
      Clock clock,
      @Value("${ayni.booking.generation-weeks:4}") int weeks) {
    this.generateHourBlocks = generateHourBlocks;
    this.patterns = patterns;
    this.identity = identity;
    this.clock = clock;
    this.weeks = weeks;
  }

  /** Generates the missing hours of one tutor of the current university, up to the horizon. */
  @Transactional
  public HoursGeneration fillFor(UUID tutorId) {
    ZoneId zone = universityZone();
    LocalDate today = LocalDate.now(clock.withZone(zone));
    return generateHourBlocks.execute(tutorId, today, lastDay(today), zone);
  }

  /**
   * Moves the horizon forward for every tutor of the current university with a window still valid.
   *
   * @return the hours created, over all of them
   */
  public int fillForCurrentUniversity() {
    ZoneId zone = universityZone();
    LocalDate today = LocalDate.now(clock.withZone(zone));
    List<UUID> tutors =
        patterns.findTutorsWithAvailabilityFrom(TenantContext.require(), today);
    int created = 0;
    for (UUID tutorId : tutors) {
      // One transaction per tutor, through the proxied use case: a tutor who fails does not undo
      // the hours of the ones before.
      created += generateHourBlocks.execute(tutorId, today, lastDay(today), zone).blocksCreated();
    }
    return created;
  }

  private LocalDate lastDay(LocalDate today) {
    return today.plusWeeks(weeks).minusDays(1);
  }

  private ZoneId universityZone() {
    return ZoneId.of(identity.requireTenant(TenantContext.require()).timezone());
  }
}
