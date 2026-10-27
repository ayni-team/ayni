package pe.ayni.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Returns to circulation the holds of the current university whose five minutes ran out.
 *
 * <p>An expired hold can already be taken over by anybody without waiting for this, so nothing is
 * wrong between the moment a hold dies and the moment this runs. What this adds is that the hour
 * reads as free again, for the tutor's agenda and for whoever looks at it.
 */
@Service
public class ReleaseExpiredHoldsUseCase {

  private final HourBlockRepository blocks;
  private final Clock clock;

  ReleaseExpiredHoldsUseCase(HourBlockRepository blocks, Clock clock) {
    this.blocks = blocks;
    this.clock = clock;
  }

  /**
   * One transaction per university: the caller binds one at a time.
   *
   * @return how many holds were released, for the log
   */
  @Transactional
  public int forCurrentUniversity() {

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    List<HourBlock> expired = blocks.findExpiredHolds(tenantId, now);
    for (HourBlock block : expired) {
      block.releaseHold(now);
    }
    return expired.size();
  }
}
