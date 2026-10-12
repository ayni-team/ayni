package pe.ayni.payments.infrastructure;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import pe.ayni.payments.application.PaymentProvider;
import pe.ayni.payments.application.PaymentProviderGateway;
import pe.ayni.payments.application.PaymentProviderUnavailable;

final class ResilientPaymentProvider implements PaymentProvider, AutoCloseable {

  private final PaymentProviderGateway gateway;
  private final PaymentProviderCircuitBreaker circuitBreaker;
  private final Duration timeout;
  private final Semaphore bulkhead;
  private final ThreadPoolExecutor workers;

  ResilientPaymentProvider(
      PaymentProviderGateway gateway,
      PaymentProviderCircuitBreaker circuitBreaker,
      Duration timeout,
      int maxConcurrentCalls) {
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    if (timeout.toMillis() < 1) {
      throw new IllegalArgumentException("timeout must be at least one millisecond");
    }
    if (maxConcurrentCalls < 1) {
      throw new IllegalArgumentException("maxConcurrentCalls must be positive");
    }
    this.gateway = gateway;
    this.circuitBreaker = circuitBreaker;
    this.timeout = timeout;
    this.bulkhead = new Semaphore(maxConcurrentCalls);
    this.workers =
        new ThreadPoolExecutor(
            maxConcurrentCalls,
            maxConcurrentCalls,
            0,
            TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            providerThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());
  }

  @Override
  public PaymentResult charge(UUID purchaseId, BigDecimal amount, String currency) {
    return call(() -> gateway.charge(purchaseId, amount, currency));
  }

  @Override
  public Optional<PaymentResult> status(UUID purchaseId) {
    return call(() -> gateway.status(purchaseId));
  }

  private <T> T call(Callable<T> request) {
    if (!bulkhead.tryAcquire()) {
      throw new PaymentProviderUnavailable("The payment provider is temporarily unavailable");
    }
    try {
      if (!circuitBreaker.tryAcquire()) {
        throw new PaymentProviderUnavailable("The payment provider is temporarily unavailable");
      }
      return executeWithinTimeout(request);
    } finally {
      bulkhead.release();
    }
  }

  private <T> T executeWithinTimeout(Callable<T> request) {
    Future<T> result;
    try {
      result = workers.submit(request);
    } catch (RejectedExecutionException rejected) {
      circuitBreaker.recordFailure();
      throw new PaymentProviderUnavailable(
          "The payment provider is temporarily unavailable", rejected);
    }

    try {
      T response = result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      circuitBreaker.recordSuccess();
      return response;
    } catch (TimeoutException timeoutFailure) {
      result.cancel(true);
      circuitBreaker.recordFailure();
      throw new PaymentProviderUnavailable(
          "The payment provider did not respond before the timeout", timeoutFailure);
    } catch (InterruptedException interrupted) {
      result.cancel(true);
      Thread.currentThread().interrupt();
      circuitBreaker.recordFailure();
      throw new PaymentProviderUnavailable(
          "The payment provider request was interrupted", interrupted);
    } catch (ExecutionException failed) {
      circuitBreaker.recordFailure();
      Throwable cause = failed.getCause();
      if (cause instanceof PaymentProviderUnavailable unavailable) {
        throw unavailable;
      }
      if (cause instanceof RuntimeException runtime) {
        throw new PaymentProviderUnavailable("The payment provider request failed", runtime);
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new PaymentProviderUnavailable("The payment provider request failed", cause);
    }
  }

  private static ThreadFactory providerThreadFactory() {
    AtomicInteger sequence = new AtomicInteger();
    return task -> {
      Thread thread = new Thread(task, "payment-provider-" + sequence.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }

  @Override
  public void close() {
    workers.shutdownNow();
  }
}
