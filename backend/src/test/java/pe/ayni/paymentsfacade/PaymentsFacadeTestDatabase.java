package pe.ayni.paymentsfacade;

import org.testcontainers.containers.PostgreSQLContainer;

final class PaymentsFacadeTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private PaymentsFacadeTestDatabase() {}
}
