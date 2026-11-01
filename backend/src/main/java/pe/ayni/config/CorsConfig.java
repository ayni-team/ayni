package pe.ayni.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

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
 *
 * <p>It is a servlet filter that runs before {@code AccessControlFilter}, not a mapping of Spring
 * MVC. The access filter answers {@code 401} and {@code 403} itself, before MVC is reached; without
 * the CORS headers on those answers the browser hides them from the web application, which then
 * reads a session that is no longer valid as a network failure and never signs the person out.
 */
@Configuration
public class CorsConfig {

  /** Before {@code AccessControlFilter}, at {@code HIGHEST_PRECEDENCE + 20}. */
  static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

  @Bean
  FilterRegistrationBean<CorsFilter> corsFilter(
      @Value("${ayni.cors.allowed-origins}") String[] allowedOrigins) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(allowedOrigins));
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.addAllowedHeader(CorsConfiguration.ALL);
    cors.addExposedHeader(HttpHeaders.CONTENT_DISPOSITION);
    cors.setMaxAge(1800L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);

    FilterRegistrationBean<CorsFilter> registration =
        new FilterRegistrationBean<>(new CorsFilter(source));
    registration.setOrder(ORDER);
    return registration;
  }
}
