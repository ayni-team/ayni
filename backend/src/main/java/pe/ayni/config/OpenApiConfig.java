package pe.ayni.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description of the API, served through Swagger UI at {@code /swagger-ui.html}.
 *
 * <p>The documentation is a deliverable of the course, and it is generated from the controllers, so
 * it stays correct as long as the annotations on them are.
 */
@Configuration
public class OpenApiConfig {

  private static final String PUBLIC_PREFIX = "/api/v1/access";

  @Bean
  OpenAPI ayniOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Ayni API")
                .version("0.1.0")
                .description(
                    "RESTful API of Ayni, an academic time bank where university students "
                        + "exchange tutoring hours as credits.")
                .contact(new Contact().name("Ayni Team")));
  }

  /**
   * Declares {@code X-Tenant-Id} and {@code X-User-Id} on every operation outside {@code
   * /api/v1/access}.
   *
   * <p>{@link TenantFilter} and {@link CurrentUserFilter} read those headers, but filters are
   * invisible to springdoc, so unless a controller declares them by hand Swagger UI offers no place
   * to type them and "Try it out" answers 400. Declaring them here once is cheaper than repeating
   * the annotation on every endpoint, and it leaves out the access routes, which are public.
   *
   * <p>It decides by path and not by controller type because {@code config} must not import the
   * internals of any module. A header an operation already declares is left alone, so the
   * controllers that spell them out keep their own description.
   *
   * <p>It goes away with TS03: once the session token carries the university and the user, nobody
   * has to type them.
   */
  @Bean
  OpenApiCustomizer requestHeadersCustomizer() {
    return openApi ->
        openApi.getPaths().entrySet().stream()
            .filter(path -> !path.getKey().startsWith(PUBLIC_PREFIX))
            .flatMap(path -> path.getValue().readOperations().stream())
            .forEach(
                operation -> {
                  addIfMissing(operation, tenantHeader());
                  addIfMissing(operation, userHeader());
                });
  }

  private static void addIfMissing(Operation operation, Parameter header) {
    boolean declared =
        operation.getParameters() != null
            && operation.getParameters().stream()
                .anyMatch(
                    existing ->
                        "header".equals(existing.getIn())
                            && header.getName().equalsIgnoreCase(existing.getName()));
    if (!declared) {
      operation.addParametersItem(header);
    }
  }

  private static Parameter tenantHeader() {
    return new HeaderParameter()
        .name(TenantFilter.TENANT_HEADER)
        .required(true)
        .schema(new StringSchema().example("UPC"))
        .description("University code. Comes from the session token once sign in is added.");
  }

  private static Parameter userHeader() {
    return new HeaderParameter()
        .name(CurrentUserFilter.USER_HEADER)
        .required(false)
        .schema(new StringSchema().format("uuid").example("11111111-1111-4111-8111-111111111111"))
        .description("Who is asking. Comes from the session token once sign in is added.");
  }
}
