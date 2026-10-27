package pe.ayni.booking.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.services.RequestedHours;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Gives back the hours a student is holding, so they return to the search at once instead of when
 * the hold runs out.
 *
 * <p>Two moments call it: the student leaving the confirmation, and a confirmation that failed. The
 * second one runs after the failed transaction has already rolled back, in a transaction of its own,
 * which is why a failure never leaves the student's hours held.
 */
@Service
public class ReleaseHoldsUseCase {

  private final HourBlockRepository blocks;

  ReleaseHoldsUseCase(HourBlockRepository blocks) {
    this.blocks = blocks;
  }

  /**
   * Releases whatever the student holds in that stretch of the tutor's hours. Hours they do not hold
   * are left alone, so asking twice is harmless.
   *
   * @return how many hours went back to circulation
   */
  @Transactional
  public int execute(UUID studentId, UUID tutorId, Instant start, int hours) {

    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(start, "start must not be null");

    String tenantId = TenantContext.require();

    int released = 0;
    for (HourBlock block :
        blocks.findWithin(tenantId, tutorId, start, RequestedHours.endOf(start, hours))) {
      if (block.releaseHoldOf(studentId)) {
        released++;
      }
    }
    return released;
  }
}
