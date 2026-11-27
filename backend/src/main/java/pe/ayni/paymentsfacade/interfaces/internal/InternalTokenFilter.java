package pe.ayni.paymentsfacade.interfaces.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * {@code /internal/**} is for the payments service, not for people (ADR 0011). No session token
 * opens it; the secret the two share in {@code X-Internal-Token} does, and nothing else.
 *
 * <p>The access filter leaves these paths to this one ({@code RouteAccess}). A call without the
 * secret, or with another one, is answered {@code 401} before any controller runs. An empty secret
 * in the configuration refuses every call. The proxy of a deployment does not forward these paths
 * at all (deploy/backend/Caddyfile): the service reaches the backend over the private network of
 * the machine, so the secret is the second lock, not the only one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
class InternalTokenFilter extends OncePerRequestFilter {

  static final String TOKEN_HEADER = "X-Internal-Token";

  private final byte[] expectedToken;
  private final ObjectMapper json;
  private final Clock clock;
  private final UrlPathHelper paths = new UrlPathHelper();

  InternalTokenFilter(
      @Value("${ayni.internal-token:}") String internalToken, ObjectMapper json, Clock clock) {
    this.expectedToken = internalToken.strip().getBytes(StandardCharsets.UTF_8);
    this.json = json;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = paths.getPathWithinApplication(request);
    return !(path.equals("/internal") || path.startsWith("/internal/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = request.getHeader(TOKEN_HEADER);
    if (expectedToken.length > 0
        && token != null
        && MessageDigest.isEqual(expectedToken, token.strip().getBytes(StandardCharsets.UTF_8))) {
      chain.doFilter(request, response);
      return;
    }
    HttpStatus status = HttpStatus.UNAUTHORIZED;
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(
        response.getOutputStream(),
        new Refusal(
            clock.instant(),
            status.value(),
            status.getReasonPhrase(),
            "Only the payments service may call this route",
            request.getRequestURI()));
  }

  private record Refusal(Instant timestamp, int status, String error, String message, String path) {}
}
