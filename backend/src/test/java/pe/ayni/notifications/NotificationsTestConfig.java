package pe.ayni.notifications;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** What the inbox scenarios add to the application: a clock they can move. */
@TestConfiguration(proxyBeanMethods = false)
class NotificationsTestConfig {

  /** Replaces the system clock everywhere, so every module agrees on now. */
  @Bean
  @Primary
  MutableClock mutableClock() {
    return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
  }
}
