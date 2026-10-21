package pe.ayni.analytics.application;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Where the uncovered demand waits to be counted, away from the search that caused it.
 *
 * <p>The search is the most measured route of the application (TS07), and its listeners run on its
 * own thread once it commits. Counting there would write to the database inside the search, and,
 * worse, would ask for a second connection while the search still holds its own: with every
 * connection of the pool held by searches that wait for another one, nothing moves until the pool
 * times out. So the search only drops a note here and answers; two worker threads count it, each
 * with a connection of its own that no search waits for.
 *
 * <p>The queue is bounded. When it is full the note is refused and the caller says so in the log:
 * a count of searches can lose a few under overload, a search must not wait. A note that is
 * accepted is processed even if it fails, and a failure never reaches the search.
 */
@Component
public class UncoveredDemandQueue {

  private static final int WORKERS = 2;
  private static final int CAPACITY = 1_000;

  private final ThreadPoolExecutor workers;
  private final Object monitor = new Object();
  private int pending;

  UncoveredDemandQueue() {
    AtomicInteger numbers = new AtomicInteger();
    workers =
        new ThreadPoolExecutor(
            WORKERS,
            WORKERS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(CAPACITY),
            task -> {
              Thread worker = new Thread(task, "uncovered-demand-" + numbers.incrementAndGet());
              worker.setDaemon(true);
              return worker;
            });
  }

  /**
   * Leaves the work to be done by a worker thread.
   *
   * @return {@code false} when the queue is full and the work was not accepted
   */
  boolean offer(Runnable work) {
    synchronized (monitor) {
      pending++;
    }
    try {
      workers.execute(
          () -> {
            try {
              work.run();
            } finally {
              finished();
            }
          });
      return true;
    } catch (RejectedExecutionException full) {
      finished();
      return false;
    }
  }

  /**
   * Waits until everything accepted so far has been processed, which is how a test knows that the
   * count it is about to read is complete without guessing a time.
   *
   * @return {@code false} when the time ran out first
   */
  public boolean awaitIdle(Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    synchronized (monitor) {
      while (pending > 0) {
        long left = deadline - System.nanoTime();
        if (left <= 0) {
          return false;
        }
        TimeUnit.NANOSECONDS.timedWait(monitor, left);
      }
      return true;
    }
  }

  private void finished() {
    synchronized (monitor) {
      pending--;
      monitor.notifyAll();
    }
  }

  @PreDestroy
  void stop() {
    workers.shutdownNow();
  }
}
