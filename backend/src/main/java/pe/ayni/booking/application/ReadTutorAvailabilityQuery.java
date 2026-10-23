package pe.ayni.booking.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.AvailabilityPause;
import pe.ayni.booking.infrastructure.AvailabilityExceptionRepository;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.booking.infrastructure.AvailabilityPauseRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Reads back the availability a tutor declared (US19, US20, US22): their weekly ranges, their date
 * exceptions and their pauses, only those that still govern their hours from today on.
 *
 * <p>Only reads. The tutor is whoever asks, and only their rows of the current university are read.
 * A range removed or a pause ended early no longer exists, so it is not there; what ended before
 * today is left out by each entity's own rule. Today is the university's day, never the server's.
 */
@Service
public class ReadTutorAvailabilityQuery {

  private static final Comparator<AvailabilityPattern> BY_WEEKDAY_AND_HOUR =
      Comparator.comparing(AvailabilityPattern::getDayOfWeek)
          .thenComparing(AvailabilityPattern::getStartsAtTime)
          .thenComparing(AvailabilityPattern::getValidFrom);

  /** By date, and on one date a whole day first, then by the hour it starts. */
  private static final Comparator<AvailabilityException> BY_DATE_AND_HOUR =
      Comparator.comparing(AvailabilityException::getExceptionDate)
          .thenComparing(
              AvailabilityException::getStartsAtTime,
              Comparator.nullsFirst(Comparator.naturalOrder()));

  private static final Comparator<AvailabilityPause> BY_START =
      Comparator.comparing(AvailabilityPause::getStartsOn);

  private final AvailabilityPatternRepository patterns;
  private final AvailabilityExceptionRepository exceptions;
  private final AvailabilityPauseRepository pauses;
  private final IdentityApi identity;
  private final Clock clock;

  ReadTutorAvailabilityQuery(
      AvailabilityPatternRepository patterns,
      AvailabilityExceptionRepository exceptions,
      AvailabilityPauseRepository pauses,
      IdentityApi identity,
      Clock clock) {
    this.patterns = patterns;
    this.exceptions = exceptions;
    this.pauses = pauses;
    this.identity = identity;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public TutorAvailabilityView execute(UUID tutorId) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");

    String tenantId = TenantContext.require();
    String timezone = identity.requireTenant(tenantId).timezone();
    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(timezone)));

    return new TutorAvailabilityView(
        timezone,
        patterns.findByTenantIdAndTutorId(tenantId, tutorId).stream()
            .filter(pattern -> pattern.isStillInForceOn(today))
            .sorted(BY_WEEKDAY_AND_HOUR)
            .map(
                pattern ->
                    new TutorAvailabilityView.WeeklyRange(
                        pattern.getId(),
                        pattern.getDayOfWeek(),
                        pattern.getStartsAtTime(),
                        pattern.getEndsAtTime(),
                        pattern.getValidFrom(),
                        pattern.getValidUntil()))
            .toList(),
        exceptions
            .findByTenantIdAndTutorIdAndExceptionDateGreaterThanEqual(tenantId, tutorId, today)
            .stream()
            .filter(exception -> exception.isStillInForceOn(today))
            .sorted(BY_DATE_AND_HOUR)
            .map(
                exception ->
                    new TutorAvailabilityView.DateException(
                        exception.getId(),
                        exception.getExceptionDate(),
                        exception.getStartsAtTime(),
                        exception.getEndsAtTime(),
                        exception.getKind()))
            .toList(),
        pauses.findByTenantIdAndTutorIdAndEndsOnGreaterThanEqual(tenantId, tutorId, today).stream()
            .filter(pause -> pause.isStillInForceOn(today))
            .sorted(BY_START)
            .map(
                pause ->
                    new TutorAvailabilityView.Pause(
                        pause.getId(), pause.getStartsOn(), pause.getEndsOn()))
            .toList());
  }
}
