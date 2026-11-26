package pe.ayni.payments.infrastructure.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.payments.tenancy.TenantContext;

/**
 * Delivers what the outbox owes the monolith, at least once and in the order it was recorded.
 *
 * <p>Every run binds each university with something due, reads a batch of its messages in a short
 * transaction, and sends them one by one with no transaction open, so a slow monolith never holds
 * one of the three connections of the pool. A message the monolith did not accept is tried again
 * after a wait that doubles with each failure, from {@code backoff-initial} up to {@code
 * backoff-max}; it is never dropped. A message whose answer was lost may arrive twice: the monolith
 * credits a purchase once however many times it hears of it (Idempotent Consumer, ADR 0011).
 */
@Component
@Lazy(false)
class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private static final int MAX_BATCHES_PER_RUN = 10;

  private final OutboxRepository outbox;
  private final MonolithEvents monolith;
  private final Clock clock;
  private final Duration backoffInitial;
  private final Duration backoffMax;
  private final int batchSize;

  OutboxRelay(
      OutboxRepository outbox,
      MonolithEvents monolith,
      Clock clock,
      @Value("${ayni.payments.outbox.backoff-initial:PT2S}") Duration backoffInitial,
      @Value("${ayni.payments.outbox.backoff-max:PT5M}") Duration backoffMax,
      @Value("${ayni.payments.outbox.batch-size:20}") int batchSize) {
    if (backoffInitial.isZero() || backoffInitial.isNegative()) {
      throw new IllegalArgumentException("backoff-initial must be positive");
    }
    if (backoffMax.compareTo(backoffInitial) < 0) {
      throw new IllegalArgumentException("backoff-max must not be shorter than backoff-initial");
    }
    if (batchSize < 1) {
      throw new IllegalArgumentException("batch-size must be positive");
    }
    this.outbox = outbox;
    this.monolith = monolith;
    this.clock = clock;
    this.backoffInitial = backoffInitial;
    this.backoffMax = backoffMax;
    this.batchSize = batchSize;
  }

  @Scheduled(
      initialDelayString = "${ayni.payments.outbox.relay-delay:PT2S}",
      fixedDelayString = "${ayni.payments.outbox.relay-delay:PT2S}")
  void relayOnSchedule() {
    relay();
  }

  /** One pass over everything due now; returns how many messages the monolith accepted. */
  int relay() {
    int delivered = 0;
    for (String tenantId : outbox.tenantsWithDueMessages(clock.instant())) {
      delivered += TenantContext.callAs(tenantId, () -> relayTenant(tenantId));
    }
    return delivered;
  }

  /**
   * Batch after batch while the monolith accepts them all, up to {@link #MAX_BATCHES_PER_RUN}, so a
   * backlog drains in one run without one university keeping the relay forever.
   */
  private int relayTenant(String tenantId) {
    int delivered = 0;
    for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
      List<OutboxMessage> due = outbox.due(tenantId, clock.instant(), batchSize);
      int accepted = relayBatch(tenantId, due);
      delivered += accepted;
      if (accepted < batchSize) {
        break;
      }
    }
    return delivered;
  }

  /** How many of the batch the monolith accepted; it stops at the first one it did not. */
  private int relayBatch(String tenantId, List<OutboxMessage> batch) {
    int delivered = 0;
    for (OutboxMessage message : batch) {
      try {
        monolith.deliver(message.eventType(), message.payload());
        outbox.markSent(message.id(), clock.instant());
        delivered++;
      } catch (RuntimeException failure) {
        int attempts = message.attempts() + 1;
        Instant next = clock.instant().plus(backoffAfter(attempts));
        outbox.markFailed(message.id(), next, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        log.warn(
            "The monolith did not accept outbox message {} ({} of tenant {}), attempt {}; next at {}",
            message.id(),
            message.eventType(),
            tenantId,
            attempts,
            next);
        // The monolith is probably down: the rest of this university waits for the next run.
        break;
      }
    }
    return delivered;
  }

  /** {@code initial * 2^(attempts - 1)}, never more than {@code max}. */
  Duration backoffAfter(int attempts) {
    Duration wait = backoffInitial;
    for (int i = 1; i < attempts && wait.compareTo(backoffMax) < 0; i++) {
      wait = wait.multipliedBy(2);
    }
    return wait.compareTo(backoffMax) > 0 ? backoffMax : wait;
  }
}
