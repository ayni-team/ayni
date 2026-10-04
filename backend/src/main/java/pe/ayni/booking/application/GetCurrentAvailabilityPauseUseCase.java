package pe.ayni.booking.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.infrastructure.AvailabilityPauseRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** Reads whether the current tutor's availability is paused today. */
@Service
public class GetCurrentAvailabilityPauseUseCase {

  private final AvailabilityPauseRepository pauses;
  private final HourBlockHorizon horizon;

  GetCurrentAvailabilityPauseUseCase(
      AvailabilityPauseRepository pauses, HourBlockHorizon horizon) {
    this.pauses = pauses;
    this.horizon = horizon;
  }

  @Transactional(readOnly = true)
  public CurrentAvailabilityPause execute(UUID tutorId) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");

    return pauses
        .findActiveOn(TenantContext.require(), tutorId, horizon.today())
        .map(
            pause ->
                new CurrentAvailabilityPause(
                    true, pause.getId(), pause.getStartsOn(), pause.getEndsOn()))
        .orElseGet(() -> new CurrentAvailabilityPause(false, null, null, null));
  }
}
