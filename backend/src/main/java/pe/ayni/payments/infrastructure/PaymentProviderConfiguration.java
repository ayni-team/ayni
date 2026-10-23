package pe.ayni.payments.infrastructure;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.ayni.payments.application.PaymentProviderGateway;

@Configuration
class PaymentProviderConfiguration {

  /** A bean of its own so the demo profile can read and reset it through {@code PaymentCircuit}. */
  @Bean
  PaymentProviderCircuitBreaker paymentProviderCircuitBreaker(
      Clock clock,
      @Value("${ayni.payments.resilience.failure-threshold:3}") int failureThreshold,
      @Value("${ayni.payments.resilience.open-duration:PT30S}") Duration openDuration) {
    return new PaymentProviderCircuitBreaker(clock, failureThreshold, openDuration);
  }

  @Bean(destroyMethod = "close")
  ResilientPaymentProvider paymentProvider(
      PaymentProviderGateway gateway,
      PaymentProviderCircuitBreaker circuitBreaker,
      @Value("${ayni.payments.resilience.timeout:PT2S}") Duration timeout,
      @Value("${ayni.payments.resilience.max-concurrent-calls:2}") int maxConcurrentCalls) {
    return new ResilientPaymentProvider(gateway, circuitBreaker, timeout, maxConcurrentCalls);
  }
}
