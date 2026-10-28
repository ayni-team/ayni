package pe.ayni.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the web application call the API from the browser.
 *
 * <p>A browser refuses a request to a different origin unless the server says otherwise, and the
 * web application runs on port 5173 while the API runs on 8080. In development the web application
 * usually goes through the Vite proxy, which makes both look like the same origin, but this keeps
 * direct calls working too.
 *
 * <p>The origins are {@code ayni.cors.allowed-origins} ({@code AYNI_ALLOWED_ORIGINS}, a comma
 * separated list, {@code http://localhost:5173} by default), the same list the live channel of the
 * whiteboards accepts. A deployment sets the address its web application is served from; any other
 * origin gets no CORS header and the browser refuses the answer.
 *
 * <p>Of the headers of an answer, a page on another origin only reads the few the browser deems
 * safe. {@code Content-Disposition}, which names a downloaded file (the indicators' CSV, an
 * evidence file), is exposed as well, so the web application keeps that name when it is served
 * from another host than the API.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private final String[] allowedOrigins;

  public CorsConfig(@Value("${ayni.cors.allowed-origins}") String[] allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION);
  }
}
