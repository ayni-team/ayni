package pe.ayni.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
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
}
