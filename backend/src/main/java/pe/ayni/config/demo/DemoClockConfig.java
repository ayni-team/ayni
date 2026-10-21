package pe.ayni.config.demo;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The clock of the {@code demo} profile, in place of the one of {@code TimeConfig}.
 *
 * <p>Every class that injects {@link Clock} gets this one, so moving it moves the time of every
 * rule at once. It starts at the real time, in UTC like the clock it replaces.
 */
@Configuration
@Profile("demo")
public class DemoClockConfig {

  @Bean
  AdjustableClock clock() {
    return new AdjustableClock(Clock.systemUTC());
  }
}
