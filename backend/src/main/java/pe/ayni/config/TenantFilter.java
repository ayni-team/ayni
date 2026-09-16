package pe.ayni.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Reads the university the request belongs to and binds it for the rest of the request.
 *
 * <p>Right now it arrives in the {@code X-Tenant-Id} header, which Postman and the web application
 * send explicitly. When authentication is added, the same value will come from a claim of the
 * access token and only this class changes: everything downstream keeps reading it from {@link
 * TenantContext}.
 *
 * <p>Clearing it in a {@code finally} block is not optional. The server reuses threads, so a tenant
 * left behind would be picked up by the next request, from another university.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

  public static final String TENANT_HEADER = "X-Tenant-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String tenantId = request.getHeader(TENANT_HEADER);
      if (tenantId != null && !tenantId.isBlank()) {
        TenantContext.set(tenantId);
      }
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }
}
