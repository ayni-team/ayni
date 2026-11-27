package pe.ayni.payments.config;

import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What stays eager under {@code spring.main.lazy-initialization}: the migrations. They run at start,
 * so a database that cannot be reached, or a migration that fails, stops the service before the
 * health check reports it ready, as it would without lazy initialization. The scheduled jobs are
 * eager by themselves (Spring Boot never makes a bean with {@code @Scheduled} lazy).
 */
@Configuration(proxyBeanMethods = false)
class StartupConfig {

  @Bean
  static LazyInitializationExcludeFilter eagerMigrations() {
    return LazyInitializationExcludeFilter.forBeanTypes(FlywayMigrationInitializer.class);
  }
}
