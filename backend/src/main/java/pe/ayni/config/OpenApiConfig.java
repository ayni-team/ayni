package pe.ayni.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
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

  /** The name the session token scheme is declared with, and referenced by. */
  public static final String BEARER_SCHEME = "bearer";

  /**
   * The routes that need no session: they are how somebody gets one, or the university an email
   * belongs to (ADR 0007).
   */
  private static final Set<String> PUBLIC_PATHS =
      Set.of(
          "/api/v1/access/request", "/api/v1/access/confirm", "/api/v1/access/university");

  /** The headers the API trusted before TS03. Nothing reads them any more. */
  private static final Set<String> RETIRED_HEADERS = Set.of("x-tenant-id", "x-user-id");

  /**
   * Jackson writes a {@link LocalTime} as {@code "HH:mm:ss"}, but springdoc has no mapping for it
   * and describes the bean ({@code hour, minute, second, nano}), so the types generated for the web
   * lie. It is resolved as a string instead, with format {@code time}; what a field says about
   * itself with {@code @Schema} (description, example, nullable) still applies. {@code LocalDate}
   * already comes out as {@code string/date}.
   */
  @Bean
  ModelConverter localTimeAsString() {
    return (type, context, chain) -> {
      if (Json.mapper().constructType(type.getType()).getRawClass() != LocalTime.class
          || !chain.hasNext()) {
        return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
      }
      AnnotatedType asString =
          new AnnotatedType()
              .type(String.class)
              .ctxAnnotations(type.getCtxAnnotations())
              .parent(type.getParent())
              .schemaProperty(type.isSchemaProperty())
              .propertyName(type.getPropertyName())
              .skipOverride(type.isSkipOverride());
      Schema<?> schema = chain.next().resolve(asString, context, chain);
      return schema == null ? null : schema.format("time");
    };
  }

  @Bean
  OpenAPI ayniOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Ayni API")
                .version("0.1.0")
                .description(
                    "RESTful API of Ayni, an academic time bank where university students "
                        + "exchange tutoring hours as credits. Every route except asking for an "
                        + "access link and opening it needs the session token that opening the "
                        + "link returns, sent as Authorization: Bearer <token>. The university and "
                        + "the person of a request are the ones of its session.")
                .contact(new Contact().name("Ayni Team")))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description(
                            "The sessionToken returned by POST /api/v1/access/confirm. Opaque: "
                                + "only its hash is stored, and signing out revokes it.")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
  }

  /**
   * Makes Swagger UI describe what TS03 built: a session token on every route but the public ones,
   * and no identity headers anywhere.
   *
   * <p>Several controllers still document {@code X-Tenant-Id} and {@code X-User-Id} by hand. They
   * are no longer read, so they are taken out of the description here, once, rather than leaving
   * Swagger UI offering fields that change nothing. The access routes that are public declare
   * that they need no token.
   *
   * <p>It decides by path and by header name, not by controller type, because {@code config} must
   * not import the internals of any module.
   */
  @Bean
  OpenApiCustomizer sessionTokenCustomizer() {
    return openApi ->
        openApi
            .getPaths()
            .forEach(
                (path, item) ->
                    item.readOperations()
                        .forEach(
                            operation -> {
                              if (operation.getParameters() != null) {
                                operation
                                    .getParameters()
                                    .removeIf(
                                        parameter ->
                                            "header".equals(parameter.getIn())
                                                && parameter.getName() != null
                                                && RETIRED_HEADERS.contains(
                                                    parameter.getName().toLowerCase()));
                              }
                              if (PUBLIC_PATHS.contains(path)) {
                                operation.setSecurity(List.of());
                              }
                            }));
  }
}
