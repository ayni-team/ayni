package pe.ayni.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

/**
 * Tests only: the fixtures of a test reach the database as its owner; the application never does.
 *
 * <p>With row level security (TS01) the application connects as {@code ayni_app} and reads only
 * the rows of the university its transaction is bound to. The scenarios written before it prepare
 * and check their data straight in the tables, through a {@code JdbcTemplate} or a repository,
 * without naming a university: as the application's role they would find nothing and could write
 * nothing. Rewriting all of them to bind a university would test nothing new about their story.
 *
 * <p>So each connection is handed to whoever asks for it by looking at who is asking: when the
 * closest class of Ayni on the stack is a test class, the fixture gets a connection of the owner,
 * which row level security does not restrict; when it is a class of the application (a controller,
 * a use case, a listener, a job, even one a test calls directly), it gets the application's role,
 * and row level security applies exactly as under {@code docker compose}. The application's own
 * classes of {@code pe.ayni.config}, and this one, are skipped: they only carry the connection to
 * whoever asked for it.
 *
 * <p>A test that is about isolation itself asks for the application's role with {@link
 * #asApplication}, so its own queries are restricted exactly as the application's are.
 *
 * <p>The application has no way to tell, and nothing of this is in {@code src/main}.
 */
@Component
class FixtureOwnerTestDataSource implements BeanPostProcessor, EnvironmentAware, DisposableBean {

  private static final String CONFIG = "pe.ayni.config.";

  private static final String SELF = FixtureOwnerTestDataSource.class.getName();

  private static final Map<String, Boolean> TEST_CLASSES = new ConcurrentHashMap<>();

  private static final ThreadLocal<Boolean> AS_APPLICATION = ThreadLocal.withInitial(() -> false);

  /** Runs the work with the connections of the application's role, as a test of isolation needs. */
  static <T> T asApplication(Supplier<T> work) {
    boolean previous = AS_APPLICATION.get();
    AS_APPLICATION.set(true);
    try {
      return work.get();
    } finally {
      AS_APPLICATION.set(previous);
    }
  }

  private Environment environment;
  private HikariDataSource owner;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof HikariDataSource application && "dataSource".equals(beanName)) {
      return new Routing(application);
    }
    return bean;
  }

  @Override
  public void destroy() {
    if (owner != null) {
      owner.close();
    }
  }

  private synchronized DataSource owner(HikariDataSource application) {
    if (owner == null) {
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl(application.getJdbcUrl());
      config.setUsername(environment.getProperty("spring.flyway.user", "test"));
      config.setPassword(environment.getProperty("spring.flyway.password", "test"));
      config.setMaximumPoolSize(4);
      config.setPoolName("test-fixtures-owner");
      owner = new HikariDataSource(config);
    }
    return owner;
  }

  private final class Routing extends DelegatingDataSource {

    private final HikariDataSource application;

    private Routing(HikariDataSource application) {
      super(application);
      this.application = application;
    }

    @Override
    public Connection getConnection() throws SQLException {
      return askedByATest() ? owner(application).getConnection() : application.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return getConnection();
    }

    /** Lets Spring Boot read the pool behind it, as it reads any wrapped data source. */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> type) throws SQLException {
      if (type.isInstance(application)) {
        return (T) application;
      }
      return super.unwrap(type);
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
      return type.isInstance(application) || super.isWrapperFor(type);
    }
  }

  private static boolean askedByATest() {
    if (AS_APPLICATION.get()) {
      return false;
    }
    Optional<String> closest =
        StackWalker.getInstance()
            .walk(
                frames ->
                    frames
                        .map(StackWalker.StackFrame::getClassName)
                        .filter(name -> name.startsWith("pe.ayni."))
                        .filter(name -> !name.startsWith(SELF))
                        .filter(name -> !name.startsWith(CONFIG) || isTestClass(name))
                        .findFirst());
    return closest.map(FixtureOwnerTestDataSource::isTestClass).orElse(false);
  }

  private static boolean isTestClass(String className) {
    String outermost = className.split("\\$")[0];
    return TEST_CLASSES.computeIfAbsent(
        outermost,
        name -> {
          URL location =
              FixtureOwnerTestDataSource.class
                  .getClassLoader()
                  .getResource(name.replace('.', '/') + ".class");
          return location != null && location.toString().contains("/test-classes/");
        });
  }
}
