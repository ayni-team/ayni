package pe.ayni.payments.tenancy;

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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gateway Offloading (ADR 0011): this service authenticates nobody. The monolith checks the
 * student's session token, as it does for every other route, and forwards the call with the
 * university, the person and a secret only the two of them share.
 *
 * <p>A call without {@code X-Internal-Token}, or with another value, is answered {@code 401} before
 * any controller runs. Only then are {@code X-Tenant-Id} and {@code X-User-Id} bound: they are
 * trusted because the monolith set them, never because a client sent them. An empty secret refuses
 * every call instead of accepting every call. The service also has no public port (deploy/backend
 * /docker-compose.yml), so the secret is the second lock, not the only one.
 *
 * <p>{@code /health} is the one exception, for the container's health check; it reads nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InternalCallFilter extends OncePerRequestFilter {

  public static final String TOKEN_HEADER = "X-Internal-Token";
  public static final String TENANT_HEADER = "X-Tenant-Id";
  public static final String USER_HEADER = "X-User-Id";

  private final byte[] expectedToken;
  private final ObjectMapper json;
  private final Clock clock;

  InternalCallFilter(
      @Value("${ayni.internal-token:}") String internalToken, ObjectMapper json, Clock clock) {
    this.expectedToken = internalToken.strip().getBytes(StandardCharsets.UTF_8);
    this.json = json;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return "/health".equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!fromTheMonolith(request.getHeader(TOKEN_HEADER))) {
      refuse(request, response);
      return;
    }
    try {
      String tenantId = request.getHeader(TENANT_HEADER);
      if (tenantId != null && !tenantId.isBlank()) {
        TenantContext.set(tenantId.strip());
      }
      String userId = request.getHeader(USER_HEADER);
      if (userId != null && !userId.isBlank()) {
        try {
          CurrentUser.set(UUID.fromString(userId.strip()));
        } catch (IllegalArgumentException notAUuid) {
          // Left unbound: the use case answers its own 400.
        }
      }
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      CurrentUser.clear();
    }
  }

  private boolean fromTheMonolith(String token) {
    if (expectedToken.length == 0 || token == null) {
      return false;
    }
    // Compared in constant time, so the answer's timing does not tell how much of a guess was right.
    return MessageDigest.isEqual(expectedToken, token.strip().getBytes(StandardCharsets.UTF_8));
  }

  private void refuse(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    HttpStatus status = HttpStatus.UNAUTHORIZED;
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(
        response.getOutputStream(),
        new Refusal(
            clock.instant(),
            status.value(),
            status.getReasonPhrase(),
            "Only the Ayni backend may call the payments service",
            request.getRequestURI()));
  }

  private record Refusal(Instant timestamp, int status, String error, String message, String path) {}
}
