package pe.ayni.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The queue that keeps the counting of uncovered demand away from the search. */
class UncoveredDemandQueueTest {

  private final UncoveredDemandQueue queue = new UncoveredDemandQueue(1, 1);

  @AfterEach
  void stopTheWorkers() {
    queue.stop();
  }

  @Test
  void whatIsAcceptedIsDoneAndAwaitIdleSaysWhen() throws InterruptedException {
    AtomicInteger done = new AtomicInteger();

    assertThat(queue.offer(done::incrementAndGet)).isTrue();
    assertThat(queue.awaitIdle(Duration.ofSeconds(10))).isTrue();

    assertThat(done).hasValue(1);
  }

  @Test
  void aFailingTaskStillLeavesTheQueueIdle() throws InterruptedException {
    assertThat(
            queue.offer(
                () -> {
                  throw new IllegalStateException("boom");
                }))
        .isTrue();

    assertThat(queue.awaitIdle(Duration.ofSeconds(10))).isTrue();
  }

  @Test
  void aFullQueueRefusesInsteadOfWaiting() throws InterruptedException {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger done = new AtomicInteger();

    // One task runs and holds the only worker, one waits in the only place, the third finds none.
    assertThat(
            queue.offer(
                () -> {
                  started.countDown();
                  await(release);
                  done.incrementAndGet();
                }))
        .isTrue();
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(queue.offer(done::incrementAndGet)).isTrue();
    assertThat(queue.offer(done::incrementAndGet)).isFalse();
    // Not idle while the worker is held: the wait says so when the time runs out.
    assertThat(queue.awaitIdle(Duration.ofMillis(50))).isFalse();

    release.countDown();

    assertThat(queue.awaitIdle(Duration.ofSeconds(10))).isTrue();
    // The refused task never ran; the two accepted ones did.
    assertThat(done).hasValue(2);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
