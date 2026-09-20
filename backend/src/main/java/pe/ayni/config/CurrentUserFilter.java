package pe.ayni.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * Reads who is making the request and binds them for the rest of it.
 *
 * <p>The companion of {@link TenantFilter}, and the same story: the value arrives in the {@code
 * X-User-Id} header today because there is no sign in yet, and when there is, it comes from a claim
 * of the access token and only this class changes. Everything downstream keeps reading it from
 * {@link CurrentUser}.
 *
 * <p>A header that is not a UUID is left unbound rather than rejected here. Refusing in a filter
 * would answer in a shape no module controls, and every endpoint that needs a user already fails
 * with {@code MissingUserException}, which each module maps for itself.
 *
 * <p>Clearing it in a {@code finally} block is not optional. The server reuses threads, so a user
 * left behind would be picked up by the next request, which is another person.
 */
@Component
public class CurrentUserFilter extends OncePerRequestFilter {

  public static final String USER_HEADER = "X-User-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      bindUserFrom(request.getHeader(USER_HEADER));
      filterChain.doFilter(request, response);
    } finally {
      CurrentUser.clear();
    }
  }

  private static void bindUserFrom(String header) {
    if (header == null || header.isBlank()) {
      return;
    }
    try {
      CurrentUser.set(UUID.fromString(header));
    } catch (IllegalArgumentException notAUuid) {
      // Left unbound on purpose: see the note above.
    }
  }
}
