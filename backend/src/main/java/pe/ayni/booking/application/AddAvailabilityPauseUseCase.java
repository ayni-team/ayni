package pe.ayni.booking.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityPause;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.infrastructure.AvailabilityPauseRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Takes a tutor out of circulation for a stretch of days.
 *
 * <p>Two pauses that overlap would say the same thing twice and make the second one impossible to
 * cancel meaningfully, so the overlap is refused. That check stays in the query: it is a range test
 * the index answers directly, and unlike the weekly windows there is no second copy of it in the
 * entity to drift from.
 */
@Service
public class AddAvailabilityPauseUseCase {

  private final AvailabilityPauseRepository pauses;
  private final Clock clock;

  AddAvailabilityPauseUseCase(AvailabilityPauseRepository pauses, Clock clock) {
    this.pauses = pauses;
    this.clock = clock;
  }

  /**
   * @throws BookingRuleViolation when the pause is backwards or runs into one already recorded
   */
  @Transactional
  public AvailabilityPause execute(UUID tutorId, LocalDate startsOn, LocalDate endsOn) {

    Objects.requireNonNull(tutorId, "tutorId must not be null");

    String tenantId = TenantContext.require();

    // Built first, so a backwards pause is refused by the entity before anything is read.
    AvailabilityPause pause =
        new AvailabilityPause(
            UUID.randomUUID(), tenantId, tutorId, startsOn, endsOn, clock.instant());

    if (!pauses.findOverlappingPauses(tenantId, tutorId, startsOn, endsOn).isEmpty()) {
      throw new BookingRuleViolation("The pause overlaps an existing availability pause");
    }

    return pauses.save(pause);
  }
}
