package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.ayni.booking.BookingTestConfig.CancelledEvents;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;

/**
 * Two cancellations of the same booking at the same instant, each on a connection of its own.
 *
 * <p>The booking is locked while it is cancelled. Without that lock both read it confirmed, both
 * return the hour, both refund and both publish; with it the second waits, finds the booking
 * cancelled and does nothing.
 */
class CancelBookingRaceTest extends BookingScenario {

  @Autowired private CancelledEvents cancelled;

  @Test
  @DisplayName("Two cancellations at once publish BookingCancelled once and refund once")
  void twoCancellationsAtOncePublishOnceAndRefundOnce() throws Exception {

    clock.set(tomorrowAt(9).minus(Duration.ofDays(1)));
    grant(ana, 5);
    hold(ana, tomorrowAt(9), 1);
    String answer =
        book(ana, tomorrowAt(9), 1, "Normal forms").andReturn().getResponse().getContentAsString();
    UUID bookingId = UUID.fromString(JsonPath.read(answer, "$.id"));
    assertThat(balanceOf(ana)).isEqualTo(4);
    clock.set(tomorrowAt(9).minus(Duration.ofHours(13)));

    CyclicBarrier bothReady = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Integer> statuses = new ArrayList<>();
    try {
      List<Future<Integer>> answers = new ArrayList<>();
      for (int request = 0; request < 2; request++) {
        answers.add(
            pool.submit(
                () -> {
                  bothReady.await();
                  return mockMvc
                      .perform(
                          delete("/api/v1/bookings/{id}", bookingId)
                              .header("X-Tenant-Id", UPC)
                              .header("X-User-Id", ana))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      for (Future<Integer> future : answers) {
        statuses.add(future.get(30, TimeUnit.SECONDS));
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(statuses).containsExactly(204, 204);
    assertThat(cancelled.of(bookingId)).hasSize(1);
    assertThat(balanceOf(ana)).isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                """
                select count(*) from wallet.ledger_entries
                where tenant_id = ? and reference_type = 'BOOKING' and reference_id = ?
                  and reason = 'BOOKING_REFUND'
                """,
                Integer.class,
                UPC,
                bookingId))
        .isEqualTo(1);
    HourBlock block = blockAt(tomorrowAt(9));
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
  }
}
