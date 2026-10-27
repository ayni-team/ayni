package pe.ayni.config;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import pe.ayni.AyniApplication;

/**
 * Tests only: starts the whole application the way {@code java -jar} does, outside the test
 * framework, for the scenarios that are about the startup itself failing.
 *
 * <p>A {@code @SpringBootTest} context that cannot start fails the test before it can say why, so
 * these scenarios call {@link org.springframework.boot.SpringApplication} themselves and look at
 * what it throws. The test framework normally keeps the {@code @TestConfiguration} classes of every
 * module out of the component scan; outside it nothing does, and their clocks would all be primary
 * at once. The filter here does what the test framework's own filter does for them.
 */
public final class StandaloneStart {

  private StandaloneStart() {}

  /** Runs the application with these command line arguments, which win over any other source. */
  public static ConfigurableApplicationContext run(String... args) {
    return new SpringApplicationBuilder(AyniApplication.class)
        .initializers(
            context ->
                context
                    .getBeanFactory()
                    .registerSingleton("standaloneStartExcludeFilter", new WithoutTestComponents()))
        .run(args);
  }

  /** Leaves out of the scan what is annotated, directly or not, as a test component. */
  private static final class WithoutTestComponents extends TypeExcludeFilter {

    @Override
    public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
      return reader.getAnnotationMetadata().isAnnotated(TestComponent.class.getName());
    }
  }
}
