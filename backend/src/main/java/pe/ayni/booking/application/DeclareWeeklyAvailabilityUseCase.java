package pe.ayni.booking.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Declares one of a tutor's weekly windows.
 *
 * <p>A tutor cannot be free twice over the same hour, so the window is refused when it runs into
 * one they already have. Whether it does is {@link AvailabilityPattern#overlaps}: the repository
 * brings back that weekday's windows and the entity decides, rather than the rule being written a
 * second time in SQL where nobody would see it change.
 */
@Service
public class DeclareWeeklyAvailabilityUseCase {

  private final AvailabilityPatternRepository patterns;
  private final HourBlockHorizon horizon;
  private final Clock clock;

  DeclareWeeklyAvailabilityUseCase(
      AvailabilityPatternRepository patterns, HourBlockHorizon horizon, Clock clock) {
    this.patterns = patterns;
    this.horizon = horizon;
    this.clock = clock;
  }

  /**
   * Saves the window and generates its hours up to the horizon in the same transaction, so they
   * can be found as soon as the tutor saves them (US19, scenario 1). A tutor without an enabled
   * skill keeps the window but gets no hours, and the answer says why (scenario 5).
   *
   * @param validUntil {@code null} for a window with no end date
   * @throws BookingRuleViolation when the window is malformed or runs into an existing one
   */
  @Transactional
  public DeclaredAvailability execute(
      UUID tutorId,
      DayOfWeek dayOfWeek,
      LocalTime startsAtTime,
      LocalTime endsAtTime,
      LocalDate validFrom,
      LocalDate validUntil) {

    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");

    String tenantId = TenantContext.require();

    // Built first, so that a malformed window is refused before anything is read, and so the
    // comparison below is between two patterns rather than a pattern and a pile of arguments.
    AvailabilityPattern declared =
        new AvailabilityPattern(
            UUID.randomUUID(),
            tenantId,
            tutorId,
            dayOfWeek,
            startsAtTime,
            endsAtTime,
            validFrom,
            validUntil,
            clock.instant());

    List<AvailabilityPattern> sameWeekday =
        patterns.findByTenantIdAndTutorIdAndDayOfWeek(
            tenantId, tutorId, (short) dayOfWeek.getValue());

    if (sameWeekday.stream().anyMatch(declared::overlaps)) {
      throw new BookingRuleViolation("The availability range overlaps an existing weekly pattern");
    }

    AvailabilityPattern saved = patterns.save(declared);
    return new DeclaredAvailability(saved, horizon.fillFor(tutorId));
  }
}
