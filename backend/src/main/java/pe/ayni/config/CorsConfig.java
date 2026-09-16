package pe.ayni.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the web application call the API from the browser.
 *
 * <p>A browser refuses a request to a different origin unless the server says otherwise, and the
 * web application runs on port 5173 while the API runs on 8080. In development the web application
 * usually goes through the Vite proxy, which makes both look like the same origin, but this keeps
 * direct calls working too.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins("http://localhost:5173")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }
}
