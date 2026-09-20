package pe.ayni.booking.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.shared.tenancy.TenantContext;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Creates a weekly availability range after checking for conflicting ranges. */
@Service
public class DeclareWeeklyAvailabilityUseCase {

    private final AvailabilityPatternRepository patternRepository;
    private final Clock clock;

    public DeclareWeeklyAvailabilityUseCase(
            AvailabilityPatternRepository patternRepository,
            Clock clock
    ) {
        this.patternRepository = patternRepository;
        this.clock = clock;
    }

    @Transactional
    public AvailabilityPattern execute(
            UUID tutorId,
            DayOfWeek dayOfWeek,
            LocalTime startsAtTime,
            LocalTime endsAtTime,
            LocalDate validFrom,
            LocalDate validUntil
    ) {
        Objects.requireNonNull(tutorId, "tutorId must not be null");
        Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
        Objects.requireNonNull(startsAtTime, "startsAtTime must not be null");
        Objects.requireNonNull(endsAtTime, "endsAtTime must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");

        String tenantId = TenantContext.require();
        List<AvailabilityPattern> overlappingPatterns =
                patternRepository.findOverlappingPatterns(
                        tenantId,
                        tutorId,
                        (short) dayOfWeek.getValue(),
                        null,
                        validFrom,
                        validUntil,
                        startsAtTime,
                        endsAtTime
                );

        if (!overlappingPatterns.isEmpty()) {
            throw new IllegalArgumentException(
                    "The availability range overlaps an existing weekly pattern"
            );
        }

        AvailabilityPattern pattern = new AvailabilityPattern(
                UUID.randomUUID(),
                tenantId,
                tutorId,
                dayOfWeek,
                startsAtTime,
                endsAtTime,
                validFrom,
                validUntil,
                Instant.now(clock)
        );

        return patternRepository.save(pattern);
    }
}
