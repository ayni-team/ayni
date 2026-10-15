package pe.ayni.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import pe.ayni.payments.application.PaymentProvider;
import pe.ayni.payments.application.PaymentProviderGateway;
import pe.ayni.payments.application.PaymentProviderUnavailable;
import org.junit.jupiter.api.Test;

class ResilientPaymentProviderTest {

  /**
   * Long enough that no build machine, however busy, takes it to start a thread or answer a mock,
   * and still far from forever: what tells a call that returned from one that waits on a provider
   * that never answers.
   */
  private static final Duration UNANSWERED = Duration.ofSeconds(5);

  @Test
  void timesOutOpensTheCircuitAndRejectsFurtherCallsWithoutQueuing() {
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    CountDownLatch releaseProvider = new CountDownLatch(1);
    AtomicInteger attempts = new AtomicInteger();
    when(gateway.charge(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              attempts.incrementAndGet();
              awaitIgnoringInterrupt(releaseProvider);
              return new PaymentProvider.PaymentResult(
                  PaymentProvider.PaymentResult.Outcome.PENDING, "late-response");
            });
    ResilientPaymentProvider provider =
        new ResilientPaymentProvider(
            gateway,
            new PaymentProviderCircuitBreaker(Clock.systemUTC(), 1, Duration.ofSeconds(30)),
            Duration.ofMillis(50),
            1);

    try {
      long startedAt = System.nanoTime();
      assertThatThrownBy(() -> provider.charge(UUID.randomUUID(), BigDecimal.ONE, "PEN"))
          .isInstanceOf(PaymentProviderUnavailable.class);
      long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

      // The provider never answers until the test releases it, so returning at all is the
      // timeout. The bound only has to tell that apart from waiting forever: a tight one measured
      // the build machine instead.
      assertThat(elapsedMillis).isLessThan(UNANSWERED.toMillis());
      // On a busy machine the worker may not have reached the provider when the timeout cancels
      // it, so the first call made zero or one attempt. What the open circuit guarantees is that
      // the second one makes none.
      int attemptsOfTheFirstCall = attempts.get();
      assertThat(attemptsOfTheFirstCall).isLessThanOrEqualTo(1);
      assertThatThrownBy(() -> provider.charge(UUID.randomUUID(), BigDecimal.ONE, "PEN"))
          .isInstanceOf(PaymentProviderUnavailable.class);
      assertThat(attempts).hasValue(attemptsOfTheFirstCall);
    } finally {
      releaseProvider.countDown();
      provider.close();
    }
  }

  @Test
  void rejectsCallsImmediatelyWhenTheBulkheadIsFull() throws Exception {
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    CountDownLatch providerEntered = new CountDownLatch(1);
    CountDownLatch releaseProvider = new CountDownLatch(1);
    AtomicInteger attempts = new AtomicInteger();
    when(gateway.charge(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              attempts.incrementAndGet();
              providerEntered.countDown();
              awaitIgnoringInterrupt(releaseProvider);
              return new PaymentProvider.PaymentResult(
                  PaymentProvider.PaymentResult.Outcome.PENDING, "slow-response");
            });
    PaymentProviderCircuitBreaker breaker =
        new PaymentProviderCircuitBreaker(Clock.systemUTC(), 3, Duration.ofSeconds(30));
    ResilientPaymentProvider provider =
        new ResilientPaymentProvider(gateway, breaker, UNANSWERED.multipliedBy(2), 1);
    var requestThread = Executors.newSingleThreadExecutor();

    try {
      var activeCall =
          requestThread.submit(
              () -> provider.charge(UUID.randomUUID(), BigDecimal.ONE, "PEN"));
      assertThat(providerEntered.await(UNANSWERED.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
      long startedAt = System.nanoTime();

      assertThatThrownBy(() -> provider.charge(UUID.randomUUID(), BigDecimal.ONE, "PEN"))
          .isInstanceOf(PaymentProviderUnavailable.class);

      // Refused without waiting for the provider's timeout, which is twice this bound.
      assertThat(Duration.ofNanos(System.nanoTime() - startedAt).toMillis())
          .isLessThan(UNANSWERED.toMillis());
      assertThat(attempts).hasValue(1);
      assertThat(breaker.stateName()).isEqualTo("CLOSED");
      releaseProvider.countDown();
      assertThat(activeCall.get(UNANSWERED.toMillis(), TimeUnit.MILLISECONDS).outcome())
          .isEqualTo(PaymentProvider.PaymentResult.Outcome.PENDING);
    } finally {
      releaseProvider.countDown();
      provider.close();
      requestThread.shutdownNow();
    }
  }

  @Test
  void automaticallyRecoversWhenTheHalfOpenRequestSucceeds() {
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    AtomicInteger attempts = new AtomicInteger();
    when(gateway.charge(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("provider is down");
              }
              return new PaymentProvider.PaymentResult(
                  PaymentProvider.PaymentResult.Outcome.CONFIRMED, "recovered");
            });
    MutableClock clock = new MutableClock();
    PaymentProviderCircuitBreaker breaker =
        new PaymentProviderCircuitBreaker(clock, 1, Duration.ofSeconds(5));
    ResilientPaymentProvider provider =
        new ResilientPaymentProvider(gateway, breaker, UNANSWERED, 1);

    try {
      assertThatThrownBy(() -> provider.charge(UUID.randomUUID(), BigDecimal.ONE, "PEN"))
          .isInstanceOf(PaymentProviderUnavailable.class);
      assertThatThrownBy(() -> provider.charge(UUID.randomUUID(), BigDecimal.ONE, "PEN"))
          .isInstanceOf(PaymentProviderUnavailable.class);
      clock.advance(Duration.ofSeconds(5));

      assertThat(provider.charge(UUID.randomUUID(), BigDecimal.ONE, "PEN").outcome())
          .isEqualTo(PaymentProvider.PaymentResult.Outcome.CONFIRMED);
      assertThat(breaker.stateName()).isEqualTo("CLOSED");
    } finally {
      provider.close();
    }
  }

  @Test
  void appliesTheSameTimeoutAndCircuitBreakerToProviderStatusChecks() {
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    when(gateway.status(any())).thenReturn(Optional.empty());
    ResilientPaymentProvider provider =
        new ResilientPaymentProvider(
            gateway,
            new PaymentProviderCircuitBreaker(Clock.systemUTC(), 2, Duration.ofSeconds(1)),
            UNANSWERED,
            1);

    try {
      assertThat(provider.status(UUID.randomUUID())).isEmpty();
    } finally {
      provider.close();
    }
  }

  private static void awaitIgnoringInterrupt(CountDownLatch latch) {
    boolean interrupted = false;
    while (latch.getCount() > 0) {
      try {
        latch.await();
      } catch (InterruptedException ignored) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private static final class MutableClock extends Clock {

    private Instant instant = Instant.parse("2026-10-06T12:00:00Z");

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
