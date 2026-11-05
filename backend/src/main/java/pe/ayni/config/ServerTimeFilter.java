package pe.ayni.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tells the web application what time the rules are reading: every answer under {@code /api/}
 * carries {@code X-Server-Time}, an ISO 8601 instant in UTC taken from the same {@link Clock} the
 * rules ask, the adjustable one under the {@code demo} profile.
 *
 * <p>A countdown in the browser (the window of a session, the expiry of a presence code) has to
 * reach zero when the server says so, and the browser's clock may be minutes off, or hours off
 * from a demo clock that was moved. The web measures the difference on each answer.
 *
 * <p>It runs after {@link CorsConfig}, which answers the preflight itself, and before {@code
 * AccessControlFilter}, so the {@code 401} and {@code 403} it answers carry the header too.
 */
@Configuration
public class ServerTimeFilter {

  /** The header, which {@link CorsConfig} exposes to a page served from another origin. */
  public static final String HEADER = "X-Server-Time";

  /** Between {@code CorsConfig}, at {@code + 10}, and {@code AccessControlFilter}, at {@code + 20}. */
  static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 15;

  @Bean
  FilterRegistrationBean<OncePerRequestFilter> serverTimeHeader(Clock clock) {
    FilterRegistrationBean<OncePerRequestFilter> registration =
        new FilterRegistrationBean<>(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                  throws ServletException, IOException {
                response.setHeader(HEADER, Instant.now(clock).toString());
                chain.doFilter(request, response);
              }
            });
    registration.addUrlPatterns("/api/*");
    registration.setOrder(ORDER);
    return registration;
  }
}
