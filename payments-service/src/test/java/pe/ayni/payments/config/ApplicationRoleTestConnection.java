package pe.ayni.payments.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.stereotype.Component;

/**
 * Tests only: the service connects to the test database as {@code ayni_payments_app}, as it does
 * under docker compose (TS01).
 *
 * <p>{@code @ServiceConnection} hands the container's own user, a superuser, which ignores row
 * level security. This replaces it with the role the migrations create without {@code BYPASSRLS};
 * Flyway keeps migrating as the container's user (config/application.properties). The same class
 * as the monolith's.
 */
@Component
class ApplicationRoleTestConnection implements BeanPostProcessor {

  static final String APPLICATION_ROLE = "ayni_payments_app";

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof JdbcConnectionDetails details) {
      return new JdbcConnectionDetails() {
        @Override
        public String getUsername() {
          return APPLICATION_ROLE;
        }

        @Override
        public String getPassword() {
          return APPLICATION_ROLE;
        }

        @Override
        public String getJdbcUrl() {
          return details.getJdbcUrl();
        }

        @Override
        public String getDriverClassName() {
          return details.getDriverClassName();
        }

        @Override
        public String getXaDataSourceClassName() {
          return details.getXaDataSourceClassName();
        }
      };
    }
    return bean;
  }
}
