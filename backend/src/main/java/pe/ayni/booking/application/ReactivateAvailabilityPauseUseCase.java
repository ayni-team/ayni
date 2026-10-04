package pe.ayni.booking.application;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityPause;
import pe.ayni.booking.infrastructure.AvailabilityPauseRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** Ends an active pause early and restores the hours the weekly rules still provide. */
@Service
public class ReactivateAvailabilityPauseUseCase {

  private final AvailabilityPauseRepository pauses;
  private final HourBlockHorizon horizon;

  ReactivateAvailabilityPauseUseCase(
      AvailabilityPauseRepository pauses, HourBlockHorizon horizon) {
    this.pauses = pauses;
    this.horizon = horizon;
  }

  @Transactional
  public ReactivatedAvailabilityPause execute(UUID tutorId, UUID pauseId) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(pauseId, "pauseId must not be null");

    String tenantId = TenantContext.require();
    LocalDate today = horizon.today();
    AvailabilityPause pause =
        pauses
            .findByTenantIdAndTutorIdAndId(tenantId, tutorId, pauseId)
            .filter(activePause -> activePause.includes(today))
            .orElseThrow(
                () -> new NoSuchElementException("Active availability pause not found: " + pauseId));

    pauses.delete(pause);
    HoursAdjustment hours = horizon.adjustFor(tutorId, today, pause.getEndsOn());
    return new ReactivatedAvailabilityPause(pause, hours);
  }
}
