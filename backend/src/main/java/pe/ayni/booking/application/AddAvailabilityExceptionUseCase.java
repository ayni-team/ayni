package pe.ayni.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.ExceptionKind;
import pe.ayni.booking.infrastructure.AvailabilityExceptionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Records that one date does not look like the tutor's usual week.
 *
 * <p>Whether the times are allowed is the entity's business, not this one's: passing a start with
 * no end is refused where the rule lives, which is also where the database enforces it.
 *
 * @see AvailabilityException
 */
@Service
public class AddAvailabilityExceptionUseCase {

  private final AvailabilityExceptionRepository exceptions;
  private final Clock clock;

  AddAvailabilityExceptionUseCase(AvailabilityExceptionRepository exceptions, Clock clock) {
    this.exceptions = exceptions;
    this.clock = clock;
  }

  /**
   * @param startsAtTime and {@code endsAtTime}: both, or neither to mean the whole day
   * @throws BookingRuleViolation when the times do not match what the kind allows
   */
  @Transactional
  public AvailabilityException execute(
      UUID tutorId,
      LocalDate exceptionDate,
      LocalTime startsAtTime,
      LocalTime endsAtTime,
      ExceptionKind kind) {

    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(kind, "kind must not be null");

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    AvailabilityException exception =
        new AvailabilityException(
            UUID.randomUUID(), tenantId, tutorId, exceptionDate, startsAtTime, endsAtTime, kind,
            now);

    return exceptions.save(exception);
  }
}
