package pe.ayni.booking.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import pe.ayni.booking.BookingScenario;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;

/**
 * The sweep that returns expired holds to circulation: that it is scheduled at all, that it walks
 * every active university binding each one, and that it leaves live holds alone.
 */
class ExpiredHoldsJobTest extends BookingScenario {

  @Autowired private ExpiredHoldsJob job;
  @Autowired private ScheduledTaskHolder scheduler;

  @Test
  @DisplayName("the sweep is registered with the scheduler")
  void theSweepIsScheduled() {
    assertThat(scheduler.getScheduledTasks())
        .anySatisfy(
            task ->
                assertThat(task.getTask().getRunnable().toString())
                    .contains("ExpiredHoldsJob.releaseExpiredHolds"));
  }

  @Test
  @DisplayName("the sweep frees the expired holds of every active university, and only those")
  void freesTheExpiredHoldsOfEveryUniversity() throws Exception {

    // An hour of the same tutor id in another university, held and abandoned there.
    String otherUniversity = "T" + UUID.randomUUID().toString().substring(0, 8);
    HourBlock elsewhere =
        new HourBlock(
            UUID.randomUUID(),
            otherUniversity,
            tutor,
            tomorrowAt(9),
            tomorrowAt(10),
            null,
            clock.instant());
    elsewhere.hold(ana, clock.instant());
    blocks.save(elsewhere);
    when(identity.activeTenantCodes()).thenReturn(List.of(UPC, otherUniversity));

    hold(ana, tomorrowAt(9), 1).andExpect(status().isCreated());
    clock.advance(Duration.ofMinutes(5).plusSeconds(1));
    hold(bruno, tomorrowAt(10), 1).andExpect(status().isCreated());

    job.releaseExpiredHolds();

    assertThat(blockAt(tomorrowAt(9)).getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(blocks.findById(elsewhere.getId()).orElseThrow().getStatus())
        .isEqualTo(HourBlockStatus.AVAILABLE);
    // Bruno's hold was taken a moment ago: it stays his.
    assertThat(blockAt(tomorrowAt(10)).getHeldBy()).isEqualTo(bruno);
  }
}
