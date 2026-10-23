package pe.ayni.skills;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * What the onboarding scenarios add to the application: one clock for every module, at noon in Lima
 * of the day the build runs, so the hours booked tomorrow never depend on the hour of the build.
 */
@TestConfiguration(proxyBeanMethods = false)
class OnboardingTestConfig {

  static final ZoneId LIMA = ZoneId.of("America/Lima");

  @Bean
  @Primary
  Clock onboardingClock() {
    Instant noon =
        ZonedDateTime.of(Instant.now().atZone(LIMA).toLocalDate(), LocalTime.NOON, LIMA).toInstant();
    return Clock.fixed(noon, ZoneOffset.UTC);
  }
}
