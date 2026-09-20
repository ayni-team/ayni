package pe.ayni.booking.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityPause;
import pe.ayni.booking.infrastructure.AvailabilityPauseRepository;
import pe.ayni.shared.tenancy.TenantContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Registers a closed inclusive date range for a tutor. */
@Service
public class AddAvailabilityPauseUseCase {

    private final AvailabilityPauseRepository pauseRepository;
    private final Clock clock;

    public AddAvailabilityPauseUseCase(
            AvailabilityPauseRepository pauseRepository,
            Clock clock
    ) {
        this.pauseRepository = pauseRepository;
        this.clock = clock;
    }

    @Transactional
    public AvailabilityPause execute(
            UUID tutorId,
            LocalDate startsOn,
            LocalDate endsOn
    ) {
        Objects.requireNonNull(tutorId, "tutorId must not be null");
        Objects.requireNonNull(startsOn, "startsOn must not be null");
        Objects.requireNonNull(endsOn, "endsOn must not be null");
        if (endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("endsOn must be on or after startsOn");
        }

        String tenantId = TenantContext.require();
        List<AvailabilityPause> overlappingPauses =
                pauseRepository.findOverlappingPauses(tenantId, tutorId, startsOn, endsOn);

        if (!overlappingPauses.isEmpty()) {
            throw new IllegalArgumentException(
                    "The pause overlaps an existing availability pause"
            );
        }

        AvailabilityPause pause = new AvailabilityPause(
                UUID.randomUUID(),
                tenantId,
                tutorId,
                startsOn,
                endsOn,
                Instant.now(clock)
        );

        return pauseRepository.save(pause);
    }
}
