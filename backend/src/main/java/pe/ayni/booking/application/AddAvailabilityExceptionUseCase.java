package pe.ayni.booking.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.ExceptionKind;
import pe.ayni.booking.infrastructure.AvailabilityExceptionRepository;
import pe.ayni.shared.tenancy.TenantContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/** Registers a date-specific addition or removal of tutor availability. */
@Service
public class AddAvailabilityExceptionUseCase {

    private final AvailabilityExceptionRepository exceptionRepository;
    private final Clock clock;

    public AddAvailabilityExceptionUseCase(
            AvailabilityExceptionRepository exceptionRepository,
            Clock clock
    ) {
        this.exceptionRepository = exceptionRepository;
        this.clock = clock;
    }

    @Transactional
    public AvailabilityException execute(
            UUID tutorId,
            LocalDate exceptionDate,
            LocalTime startsAtTime,
            LocalTime endsAtTime,
            ExceptionKind kind
    ) {
        Objects.requireNonNull(tutorId, "tutorId must not be null");
        Objects.requireNonNull(exceptionDate, "exceptionDate must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        if ((startsAtTime == null) != (endsAtTime == null)) {
            throw new IllegalArgumentException(
                    "startsAtTime and endsAtTime must both be provided or both be null"
            );
        }

        String tenantId = TenantContext.require();
        Instant now = Instant.now(clock);
        AvailabilityException exception;

        if (kind == ExceptionKind.ADD) {
            exception = AvailabilityException.addSlot(
                    UUID.randomUUID(),
                    tenantId,
                    tutorId,
                    exceptionDate,
                    startsAtTime,
                    endsAtTime,
                    now
            );
        } else if (startsAtTime == null && endsAtTime == null) {
            exception = AvailabilityException.removeWholeDay(
                    UUID.randomUUID(),
                    tenantId,
                    tutorId,
                    exceptionDate,
                    now
            );
        } else {
            exception = AvailabilityException.removeSlot(
                    UUID.randomUUID(),
                    tenantId,
                    tutorId,
                    exceptionDate,
                    startsAtTime,
                    endsAtTime,
                    now
            );
        }

        return exceptionRepository.save(exception);
    }
}
