package pe.ayni.booking;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingConfirmed;

/**
 * What the booking scenarios add to the application: a clock they can move, and a way to make a
 * confirmation fail at its very last moment.
 */
@TestConfiguration(proxyBeanMethods = false)
public class BookingTestConfig {

  /** Replaces the system clock everywhere, wallet included, so every module agrees on now. */
  @Bean
  @Primary
  MutableClock mutableClock() {
    return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
  }

  @Bean
  FailingConfirmation failingConfirmation() {
    return new FailingConfirmation();
  }

  /** Counts what was published, from whichever thread published it. */
  @Bean
  CancelledEvents cancelledEvents() {
    return new CancelledEvents();
  }

  /**
   * Every {@code BookingCancelled} that committed, by booking.
   *
   * <p>{@code ApplicationEvents} only sees what the test thread published, and the race tests
   * publish from worker threads.
   */
  public static class CancelledEvents {

    private final List<BookingCancelled> published = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(BookingCancelled event) {
      published.add(event);
    }

    public List<BookingCancelled> of(UUID bookingId) {
      return published.stream().filter(event -> event.bookingId().equals(bookingId)).toList();
    }
  }

  /**
   * Makes the confirmation of a chosen student fail just before its transaction commits.
   *
   * <p>That is the worst moment to fail: the credits are charged, the hours are marked and the
   * booking is saved, all of it flushed to the database. Scenario 6 has to show that even then the
   * balance ends as it was and the hours end free.
   */
  public static class FailingConfirmation {

    private final Set<UUID> doomed = ConcurrentHashMap.newKeySet();

    @PersistenceContext private EntityManager entityManager;

    /** The next confirmation of this student fails. */
    public void failNextConfirmationOf(UUID studentId) {
      doomed.add(studentId);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void on(BookingConfirmed event) {
      if (doomed.remove(event.studentId())) {
        // Everything the confirmation wrote reaches the database first, so the rollback has real
        // rows to undo and not only what Hibernate was still holding in memory.
        entityManager.flush();
        throw new IllegalStateException("Simulated failure while committing the booking");
      }
    }
  }
}
