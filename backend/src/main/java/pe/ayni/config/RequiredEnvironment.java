package pe.ayni.config;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when a variable the active profile needs is not set, and says which.
 *
 * <p>The variables are the list {@code ayni.required-environment}, which only {@code
 * application-prod.yml} declares: development and the tests fall back on the values of {@code
 * application.yml} and need none. Without this, a missing database address would surface as a
 * driver error about a URL that reads {@code ${AYNI_DB_URL}}, after a connection was attempted; a
 * missing secret would not surface at all until the first person joined a video call.
 *
 * <p>It runs before any bean is created, so nothing has connected to anything when it refuses. A
 * blank value counts as missing, since that is what an unset variable becomes in most deployment
 * files.
 */
@Component
public class RequiredEnvironment implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

  static final String PROPERTY = "ayni.required-environment";

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    List<String> missing = missingIn(environment);
    if (!missing.isEmpty()) {
      throw new MissingEnvironmentException(missing);
    }
  }

  /** The variables of the list that have no value, in the order the list names them. */
  static List<String> missingIn(Environment environment) {
    List<String> required =
        Binder.get(environment).bind(PROPERTY, Bindable.listOf(String.class)).orElse(List.of());
    return required.stream()
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .filter(name -> !hasValue(environment, name))
        .toList();
  }

  private static boolean hasValue(Environment environment, String name) {
    String value = environment.getProperty(name);
    return value != null && !value.isBlank();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  /** The startup was refused: the message names every variable that is missing. */
  public static class MissingEnvironmentException extends IllegalStateException {

    MissingEnvironmentException(List<String> missing) {
      super(
          "Missing required environment variables: "
              + String.join(", ", missing)
              + ". Set them before starting; the Production section of README.md lists every"
              + " variable.");
    }
  }
}
