package pe.ayni.payments;

import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import pe.ayni.payments.tenancy.TenantTransactionManager;

/**
 * The payments service: credit purchases and the payment provider, extracted from the modular
 * monolith along the boundary the {@code payments} module already had (ADR 0002, ADR 0011).
 */
@SpringBootApplication
@EnableScheduling
public class PaymentsServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentsServiceApplication.class, args);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** Every transaction carries the university bound to its thread (TS01). */
  @Bean
  PlatformTransactionManager transactionManager(DataSource dataSource) {
    return new TenantTransactionManager(dataSource);
  }
}
