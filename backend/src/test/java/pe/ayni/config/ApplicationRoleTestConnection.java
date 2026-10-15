package pe.ayni.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.stereotype.Component;

/**
 * Tests only: the application connects to the test database as {@code ayni_app}, as it does under
 * {@code docker compose} (TS01).
 *
 * <p>Every integration test starts its PostgreSQL with {@code @ServiceConnection}, whose connection
 * details carry the container's own user, a superuser. A superuser ignores row level security, so a
 * test connected as it would prove nothing about isolation. This replaces the user and the
 * password of those details with the role of the application, which the migrations create without
 * {@code BYPASSRLS}; Flyway keeps migrating as the container's user, set in {@code
 * config/application.properties}, which is the owner of everything it creates.
 */
@Component
class ApplicationRoleTestConnection implements BeanPostProcessor {

  static final String APPLICATION_ROLE = "ayni_app";

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
