package pe.ayni.identity.domain.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Who may call a route, decided by the group the path belongs to (ADR 0007).
 *
 * <p>A role says what kind of person somebody is, never that a booking or a session is theirs:
 * ownership stays in the use cases, which are the only ones with the data to decide it. This only
 * keeps a student out of the coordinator's routes, a coordinator out of the administrator's, and
 * everybody without a session out of everything that is not deliberately public.
 *
 * <ul>
 *   <li>{@code /api/v1/access/request}, {@code /api/v1/access/confirm} and {@code
 *       /api/v1/access/university}, Swagger and the health check are public: they are how somebody
 *       gets a session, the name and colours of the university an email belongs to, or they
 *       describe the platform.
 *   <li>{@code /api/v1/admin/**} requires {@code PLATFORM_ADMIN}.
 *   <li>{@code /api/v1/coordinator/**} requires {@code COORDINATOR}.
 *   <li>Signing out, and any path outside {@code /api}, requires a session of any kind.
 *   <li>{@code GET /api/v1/me}, {@code GET /api/v1/university} and {@code
 *       /api/v1/notifications/**} are for a student or a coordinator: the backend guide gives them
 *       to whoever is signed in into a university.
 *   <li>Every other {@code /api} route requires {@code STUDENT}.
 * </ul>
 */
public final class RouteAccess {

    private static final List<String> PUBLIC_PATHS =
            List.of(
                    "/api/v1/access/request",
                    "/api/v1/access/confirm",
                    "/api/v1/access/university",
                    "/swagger-ui.html",
                    "/swagger-ui",
                    "/v3/api-docs",
                    "/actuator/health");

    private static final Set<AccessRole> ANYBODY = EnumSet.allOf(AccessRole.class);

    private static final Set<AccessRole> MEMBERS =
            EnumSet.of(AccessRole.STUDENT, AccessRole.COORDINATOR);

    private final Set<AccessRole> allowed;

    private RouteAccess(Set<AccessRole> allowed) {
        this.allowed = allowed;
    }

    /** What the route needs. The path is the one inside the application, already decoded. */
    public static RouteAccess of(String method, String path) {
        if (PUBLIC_PATHS.stream().anyMatch(prefix -> within(path, prefix))) {
            return new RouteAccess(null);
        }
        if (within(path, "/api/v1/admin")) {
            return new RouteAccess(EnumSet.of(AccessRole.PLATFORM_ADMIN));
        }
        if (within(path, "/api/v1/coordinator")) {
            return new RouteAccess(EnumSet.of(AccessRole.COORDINATOR));
        }
        if (within(path, "/api/v1/access") || !within(path, "/api")) {
            return new RouteAccess(ANYBODY);
        }
        if (("GET".equalsIgnoreCase(method)
                        && (path.equals("/api/v1/me") || path.equals("/api/v1/university")))
                || within(path, "/api/v1/notifications")) {
            return new RouteAccess(MEMBERS);
        }
        return new RouteAccess(EnumSet.of(AccessRole.STUDENT));
    }

    /** Whether anybody may call it, with or without a session. */
    public boolean isPublic() {
        return allowed == null;
    }

    /** Whether a session of this kind may call it. A public route lets everybody in. */
    public boolean allows(AccessRole role) {
        return allowed == null || allowed.contains(role);
    }

    /** The roles it accepts, for the message of a refusal; empty when it is public. */
    public Set<AccessRole> allowedRoles() {
        return allowed == null ? Set.of() : Set.copyOf(allowed);
    }

    private static boolean within(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
