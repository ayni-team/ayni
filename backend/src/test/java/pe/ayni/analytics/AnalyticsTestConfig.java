package pe.ayni.analytics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** What the scenarios of analytics that start from a search add to the application: a clock. */
@TestConfiguration(proxyBeanMethods = false)
class AnalyticsTestConfig {

  /** Replaces the system clock everywhere, so the search and the projection agree on now. */
  @Bean
  @Primary
  MutableClock mutableClock() {
    return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
  }
}
