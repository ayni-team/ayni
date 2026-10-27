package pe.ayni.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Isolation between universities enforced by the database (TS01, ADR 0007).
 *
 * <p>Replaces the transaction manager Spring Boot would create with {@link
 * TenantTransactionManager}, the same JPA one plus the university of each transaction. The bean
 * keeps the name {@code transactionManager}, the one Spring Data repositories ask for, so every
 * repository call, every use case and every listener goes through it.
 *
 * <p>The rest lives in the database: the policies of each table and the two roles, the owner that
 * runs Flyway and the role without {@code BYPASSRLS} the application connects with. See {@code
 * V202610150020__tenancy_enable_row_level_security.sql}.
 */
@Configuration
public class TenantIsolationConfig {

  @Bean
  PlatformTransactionManager transactionManager(
      EntityManagerFactory entityManagerFactory,
      ObjectProvider<TransactionManagerCustomizers> customizers) {
    TenantTransactionManager transactionManager = new TenantTransactionManager();
    transactionManager.setEntityManagerFactory(entityManagerFactory);
    customizers.ifAvailable(customizer -> customizer.customize(transactionManager));
    return transactionManager;
  }
}
