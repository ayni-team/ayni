package pe.ayni.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** What the tests of isolation add to the application: a clock they can move. */
@TestConfiguration(proxyBeanMethods = false)
class TenancyTestConfig {

  @Bean
  @Primary
  TenancyMutableClock tenancyMutableClock() {
    return new TenancyMutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
  }
}
