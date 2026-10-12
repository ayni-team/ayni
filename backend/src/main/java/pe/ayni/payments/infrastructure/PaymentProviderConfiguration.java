package pe.ayni.payments.infrastructure;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.ayni.payments.application.PaymentProviderGateway;

@Configuration
class PaymentProviderConfiguration {

  @Bean(destroyMethod = "close")
  ResilientPaymentProvider paymentProvider(
      PaymentProviderGateway gateway,
      Clock clock,
      @Value("${ayni.payments.resilience.failure-threshold:3}") int failureThreshold,
      @Value("${ayni.payments.resilience.open-duration:PT30S}") Duration openDuration,
      @Value("${ayni.payments.resilience.timeout:PT2S}") Duration timeout,
      @Value("${ayni.payments.resilience.max-concurrent-calls:2}") int maxConcurrentCalls) {
    PaymentProviderCircuitBreaker circuitBreaker =
        new PaymentProviderCircuitBreaker(clock, failureThreshold, openDuration);
    return new ResilientPaymentProvider(gateway, circuitBreaker, timeout, maxConcurrentCalls);
  }
}
