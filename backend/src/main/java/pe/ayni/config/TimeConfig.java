package pe.ayni.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock the application reads time from.
 *
 * <p>Every rule that depends on time asks for a {@link Clock} instead of calling {@code
 * Instant.now()}, so that a test can decide what "now" is: credits that expire tomorrow, a
 * cancellation eleven hours before the session. A rule that reads the clock statically can only be
 * tested by waiting.
 *
 * <p>UTC, because every instant stored and returned by the API is in UTC.
 */
@Configuration
public class TimeConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
