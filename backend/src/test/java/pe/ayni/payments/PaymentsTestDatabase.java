package pe.ayni.payments;

import org.testcontainers.containers.PostgreSQLContainer;

final class PaymentsTestDatabase {

  static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private PaymentsTestDatabase() {}
}
