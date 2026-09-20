package pe.ayni.booking.domain.services;

import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.AvailabilityPause;
import pe.ayni.booking.domain.model.ExceptionKind;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;

/**
 * Domain service responsible for generating concrete one-hour inventory blocks
 * by combining weekly recurring patterns, date exceptions, and pauses.
 */
@Component
public class BlockGenerator {

    public List<HourBlock> generate(
            List<AvailabilityPattern> patterns,
            List<AvailabilityException> exceptions,
            List<AvailabilityPause> pauses,
            LocalDate from,
            LocalDate to,
            ZoneId zone,
            Instant now
    ) {
        Objects.requireNonNull(patterns, "patterns must not be null");
        Objects.requireNonNull(exceptions, "exceptions must not be null");
        Objects.requireNonNull(pauses, "pauses must not be null");
        Objects.requireNonNull(from, "from date must not be null");
        Objects.requireNonNull(to, "to date must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to date must be on or after from date");
        }

        List<HourBlock> generatedBlocks = new ArrayList<>();

        // Iterate day by day over the specified date range.
        for (LocalDate currentDate = from; !currentDate.isAfter(to); currentDate = currentDate.plusDays(1)) {
            final LocalDate date = currentDate;

            // 1. If the date falls within a pause, the day is completely ignored.
            boolean isPaused = pauses.stream().anyMatch(pause -> pause.includes(date));
            if (isPaused) {
                continue;
            }

            DayOfWeek currentDayOfWeek = date.getDayOfWeek();

            // 2. Get active patterns for this day of the week
            List<AvailabilityPattern> activePatterns = patterns.stream()
                    .filter(p -> p.getDayOfWeek() == currentDayOfWeek)
                    .filter(p -> !date.isBefore(p.getValidFrom()))
                    .filter(p -> p.getValidUntil() == null || !date.isAfter(p.getValidUntil()))
                    .toList();

            // 3. Get exceptions for this specific date
            List<AvailabilityException> dateExceptions = exceptions.stream()
                    .filter(e -> e.getExceptionDate().equals(date))
                    .toList();

            // If there is a REMOVE exception for the entire day (without defined hours), the day is discarded
            boolean fullDayRemoved = dateExceptions.stream()
                    .anyMatch(e -> e.getKind() == ExceptionKind.REMOVE
                            && e.getStartsAtTime() == null
                            && e.getEndsAtTime() == null);
            if (fullDayRemoved) {
                continue;
            }

            // 4. Generate the 1-hour base blocks from the weekly patterns.
            for (AvailabilityPattern pattern : activePatterns) {
                LocalTime cursor = pattern.getStartsAtTime();
                while (!cursor.plusHours(1).isAfter(pattern.getEndsAtTime())) {
                    LocalTime blockStart = cursor;
                    LocalTime blockEnd = cursor.plusHours(1);

                    // Check if this specific block was canceled by a REMOVE exception.
                    boolean isSlotRemoved = dateExceptions.stream()
                            .anyMatch(e -> e.getKind() == ExceptionKind.REMOVE
                                    && e.affects(blockStart, blockEnd));

                    if (!isSlotRemoved) {
                        Instant startInstant = ZonedDateTime.of(date, blockStart, zone).toInstant();
                        Instant endInstant = ZonedDateTime.of(date, blockEnd, zone).toInstant();

                        generatedBlocks.add(new HourBlock(
                                UUID.randomUUID(),
                                pattern.getTenantId(),
                                pattern.getTutorId(),
                                startInstant,
                                endInstant,
                                pattern.getId(),
                                now
                        ));
                    }
                    cursor = cursor.plusHours(1);
                }
            }

            // 5. Add extraordinary blocks defined with ADD exceptions.
            dateExceptions.stream()
                    .filter(e -> e.getKind() == ExceptionKind.ADD)
                    .forEach(e -> {
                        LocalTime cursor = e.getStartsAtTime();
                        while (!cursor.plusHours(1).isAfter(e.getEndsAtTime())) {
                            Instant startInstant = ZonedDateTime.of(date, cursor, zone).toInstant();
                            Instant endInstant = ZonedDateTime.of(date, cursor.plusHours(1), zone).toInstant();

                            generatedBlocks.add(new HourBlock(
                                    UUID.randomUUID(),
                                    e.getTenantId(),
                                    e.getTutorId(),
                                    startInstant,
                                    endInstant,
                                    null, // Without an associated recurring pattern
                                    now
                            ));
                            cursor = cursor.plusHours(1);
                        }
                    });
        }

        return generatedBlocks;
    }
}