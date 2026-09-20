package pe.ayni.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides application-wide time configuration.
 *
 * <p>Using an injectable clock keeps time-dependent application and domain logic deterministic
 * and makes it possible to replace the clock in tests.
 */
@Configuration
public class TimeConfig {

    /** Uses UTC as the system clock so persisted timestamps have a consistent reference zone. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
