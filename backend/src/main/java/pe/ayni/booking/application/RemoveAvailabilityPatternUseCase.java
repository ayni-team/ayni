package pe.ayni.booking.application;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** Removes a tutor's entire weekly availability range and reconciles its generated hours. */
@Service
public class RemoveAvailabilityPatternUseCase {

  private final AvailabilityPatternRepository patterns;
  private final HourBlockRepository blocks;
  private final HourBlockHorizon horizon;

  RemoveAvailabilityPatternUseCase(
      AvailabilityPatternRepository patterns, HourBlockRepository blocks, HourBlockHorizon horizon) {
    this.patterns = patterns;
    this.blocks = blocks;
    this.horizon = horizon;
  }

  @Transactional
  public RemovedAvailabilityPattern execute(UUID tutorId, UUID patternId) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(patternId, "patternId must not be null");

    String tenantId = TenantContext.require();
    AvailabilityPattern pattern =
        patterns
            .findByTenantIdAndTutorIdAndId(tenantId, tutorId, patternId)
            .orElseThrow(
                () -> new NoSuchElementException("Availability pattern not found: " + patternId));
    LocalDate from = pattern.getValidFrom();
    LocalDate until = pattern.getValidUntil();

    blocks.detachPattern(tenantId, tutorId, patternId);
    patterns.delete(pattern);
    patterns.flush();
    HoursAdjustment hours =
        until == null
            ? horizon.adjustFor(tutorId, from)
            : horizon.adjustFor(tutorId, from, until);
    return new RemovedAvailabilityPattern(pattern, hours);
  }
}
