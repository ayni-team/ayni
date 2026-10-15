package pe.ayni.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.ayni.identity.interfaces.web.AccessControlFilter;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Tests only: keeps the {@code X-Tenant-Id} and {@code X-User-Id} headers working for the tests
 * written before TS03.
 *
 * <p>The application no longer reads them: identity's {@link AccessControlFilter} binds the
 * university and the person from the session token. The scenarios of every module were written
 * against the headers, and what they prove is the rule of their use case, not how the request got
 * in, so rewriting them to sign in first would add a link and a session to each and test nothing
 * new. This filter lives in {@code src/test} and is never part of the application.
 *
 * <p>A request with no {@code Authorization} header is bound from the two headers, exactly as the
 * old filters did (a value that is missing or not a UUID is left unbound, so the use cases still
 * answer their own 400), and it is marked as already filtered by {@link AccessControlFilter}, which
 * then lets it through untouched: that is how {@code OncePerRequestFilter} works, so the
 * application has no switch of its own for tests. A request that carries a token goes through the
 * real filter.
 *
 * <p>The tests of TS03 itself turn it off with {@code ayni.test.identity-headers=false}, so they
 * prove the real filter: no token, no entry.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "ayni.test.identity-headers", havingValue = "true", matchIfMissing = true)
public class IdentityHeadersTestFilter extends OncePerRequestFilter {

  public static final String TENANT_HEADER = "X-Tenant-Id";
  public static final String USER_HEADER = "X-User-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
      chain.doFilter(request, response);
      return;
    }

    request.setAttribute(
        AccessControlFilter.NAME + ALREADY_FILTERED_SUFFIX, Boolean.TRUE);
    try {
      String tenantId = request.getHeader(TENANT_HEADER);
      if (tenantId != null && !tenantId.isBlank()) {
        TenantContext.set(tenantId);
      }
      String userId = request.getHeader(USER_HEADER);
      if (userId != null && !userId.isBlank()) {
        try {
          CurrentUser.set(UUID.fromString(userId));
        } catch (IllegalArgumentException notAUuid) {
          // Left unbound, as the old filter did: the use case answers its own 400.
        }
      }
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      CurrentUser.clear();
    }
  }
}
