package pe.ayni.booking.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.AvailabilityPause;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.services.BlockGenerator;
import pe.ayni.booking.infrastructure.AvailabilityExceptionRepository;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.booking.infrastructure.AvailabilityPauseRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.shared.events.HoursGenerated;
import pe.ayni.shared.tenancy.TenantContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Coordinates the generation and persistence of one-hour availability blocks.
 *
 * <p>The domain service calculates the blocks, while this application service loads the required
 * data, keeps the operation transactional, prevents duplicate inventory, and publishes the
 * resulting event.
 */
@Service
public class GenerateHourBlocksUseCase {

    private final AvailabilityPatternRepository patternRepository;
    private final AvailabilityExceptionRepository exceptionRepository;
    private final AvailabilityPauseRepository pauseRepository;
    private final HourBlockRepository hourBlockRepository;
    private final BlockGenerator blockGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public GenerateHourBlocksUseCase(
            AvailabilityPatternRepository patternRepository,
            AvailabilityExceptionRepository exceptionRepository,
            AvailabilityPauseRepository pauseRepository,
            HourBlockRepository hourBlockRepository,
            BlockGenerator blockGenerator,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.patternRepository = patternRepository;
        this.pauseRepository = pauseRepository;
        this.exceptionRepository = exceptionRepository;
        this.hourBlockRepository = hourBlockRepository;
        this.blockGenerator = blockGenerator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Generates hour blocks for a tutor within the requested date range.
     *
     * <p>Only blocks that do not already exist for the tenant, tutor, and start instant are
     * persisted. This makes repeated executions safe when availability is regenerated or a
     * scheduled job processes the same horizon more than once.
     */
    @Transactional
    public void execute(
            UUID tutorId,
            LocalDate from,
            LocalDate to,
            ZoneId zone
    ) {
        String tenantId = TenantContext.require();
        Instant now = clock.instant();

        List<AvailabilityPattern> patterns =
                patternRepository.findActiveInHorizon(tenantId, tutorId, from, to);

        List<AvailabilityException> exceptions =
                exceptionRepository.findWithinHorizon(
                        tenantId, tutorId, from, to);

        List<AvailabilityPause> pauses =
                pauseRepository.findOverlappingPauses(
                        tenantId, tutorId, from, to);

        List<HourBlock> blocks = blockGenerator.generate(
                patterns,
                exceptions,
                pauses,
                from,
                to,
                zone,
                now
        );

        // Avoid unnecessary database queries and empty events when no availability can be generated.
        if (blocks.isEmpty()) {
            return;
        }

        // Query the complete local-date range so blocks at any time within the horizon are covered.
        Instant existingFrom = ZonedDateTime.of(from, LocalTime.MIN, zone).toInstant();
        Instant existingTo = ZonedDateTime.of(to, LocalTime.MAX, zone).toInstant();

        Set<Instant> existingStarts = hourBlockRepository
                .findByTenantIdAndTutorIdAndStartsAtBetween(
                        tenantId, tutorId, existingFrom, existingTo)
                .stream()
                .map(HourBlock::getStartsAt)
                .collect(Collectors.toSet());

        // The set also removes duplicate starts produced during this generation run.
        Set<Instant> startsToSave = new HashSet<>(existingStarts);
        List<HourBlock> newBlocks = blocks.stream()
                .filter(block -> startsToSave.add(block.getStartsAt()))
                .toList();

        // Do not persist or publish anything when the horizon is already materialized.
        if (newBlocks.isEmpty()) {
            return;
        }

        hourBlockRepository.saveAll(newBlocks);

        // Matching consumes this event to add the newly available blocks to its read model.
        eventPublisher.publishEvent(
                new HoursGenerated(
                        tenantId,
                        tutorId,
                        newBlocks.stream()
                                .map(block -> new HoursGenerated.Block(
                                        block.getId(),
                                        block.getStartsAt()
                                ))
                                .toList(),
                        now
                )
        );
    }
}