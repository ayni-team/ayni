package pe.ayni.identity.interfaces.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import pe.ayni.identity.application.ResolveSessionQuery;
import pe.ayni.identity.domain.model.RouteAccess;
import pe.ayni.identity.domain.model.SessionResolution;
import pe.ayni.identity.interfaces.rest.ApiError;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * TS03: every request proves who it is with its session token before it reaches a controller.
 *
 * <p>The token travels as {@code Authorization: Bearer <token>}. It is resolved by its hash in
 * {@code identity.user_sessions}, and the university and the person of the session are bound to
 * {@link TenantContext} and {@link CurrentUser} for the rest of the request. Nothing else binds
 * them: {@code X-Tenant-Id} and {@code X-User-Id} are no longer read (ADR 0007), so the university
 * of a request is the one of its session and nobody can name another.
 *
 * <p>The route decides who may call it ({@link RouteAccess}). A request without a token, or with
 * one that is unknown, expired or revoked, is answered {@code 401}; a session of the wrong kind for
 * the route, {@code 403}. Either way the controller is never reached, so nothing is executed and
 * nothing is read on behalf of the request. Both answers have the shape every error has: {@code
 * timestamp}, {@code status}, {@code error}, {@code message} and {@code path}.
 *
 * <p>Ownership is not decided here: that a booking or a session is the caller's is checked by the
 * use case, the only one with the data to decide it.
 *
 * <p>Clearing the context in a {@code finally} block is not optional. The server reuses threads, so
 * a person left bound would be picked up by the next request, which is somebody else.
 */
@Component(AccessControlFilter.NAME)
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AccessControlFilter extends OncePerRequestFilter {

    /** The bean name, which is also the name the already filtered marker is derived from. */
    public static final String NAME = "accessControlFilter";

    private static final String BEARER = "Bearer ";

    private final ResolveSessionQuery resolveSession;
    private final ObjectMapper json;
    private final Clock clock;
    private final UrlPathHelper paths = new UrlPathHelper();

    AccessControlFilter(ResolveSessionQuery resolveSession, ObjectMapper json, Clock clock) {
        this.resolveSession = resolveSession;
        this.json = json;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // A CORS preflight carries no credentials by definition; the request it announces does.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = paths.getPathWithinApplication(request);
        if (hasDotSegment(path)) {
            refuse(response, request, HttpStatus.BAD_REQUEST, "The path is not valid");
            return;
        }

        RouteAccess access = RouteAccess.of(request.getMethod(), path);
        if (access.isPublic()) {
            chain.doFilter(request, response);
            return;
        }

        String token = bearerToken(request);
        if (token == null) {
            refuse(
                    response,
                    request,
                    HttpStatus.UNAUTHORIZED,
                    "Sign in first: send the session token as Authorization: Bearer <token>");
            return;
        }

        SessionResolution session = resolveSession.execute(token);
        if (!session.isOpen()) {
            refuse(response, request, HttpStatus.UNAUTHORIZED, messageFor(session.outcome()));
            return;
        }

        if (!access.allows(session.role())) {
            refuse(
                    response,
                    request,
                    HttpStatus.FORBIDDEN,
                    "This route is for "
                            + access.allowedRoles().stream()
                                    .map(Enum::name)
                                    .sorted()
                                    .collect(Collectors.joining(" or ")));
            return;
        }

        try {
            if (session.tenantId() != null) {
                TenantContext.set(session.tenantId());
            }
            CurrentUser.set(session.subjectId());
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            CurrentUser.clear();
        }
    }

    /** The token of the {@code Authorization} header, or {@code null} when there is none. */
    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static String messageFor(SessionResolution.Outcome outcome) {
        return switch (outcome) {
            case EXPIRED -> "The session expired. Sign in again";
            case REVOKED -> "The session was closed. Sign in again";
            default -> "The session token is not valid. Sign in again";
        };
    }

    /**
     * A path with a {@code .} or {@code ..} segment could be read as one group here and resolved
     * as another further on. No route of the API has one, so it is refused before it is matched.
     */
    private static boolean hasDotSegment(String path) {
        return Arrays.stream(path.split("/")).anyMatch(segment -> segment.equals(".") || segment.equals(".."));
    }

    private void refuse(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            String message)
            throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        json.writeValue(
                response.getOutputStream(),
                new ApiError(
                        clock.instant(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        request.getRequestURI()));
    }
}
