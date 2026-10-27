package pe.ayni.booking.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.ayni.booking.domain.services.BlockGenerator;

/**
 * Makes this module's domain services injectable without letting Spring into the domain.
 *
 * <p>Annotating {@link BlockGenerator} with {@code @Component} would be shorter and would put a
 * framework import in {@code domain/services}, where the backend guide keeps the rules free of one.
 * Declaring the bean from infrastructure costs this file and nothing else.
 */
@Configuration
class BookingDomainConfig {

  @Bean
  BlockGenerator blockGenerator() {
    return new BlockGenerator();
  }
}
