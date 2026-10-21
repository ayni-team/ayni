package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.ayni.identity.domain.model.AccessRole;
import pe.ayni.identity.domain.model.RouteAccess;

/** The route groups of ADR 0007, without Spring. */
class RouteAccessTest {

    @ParameterizedTest(name = "{0} {1} is public")
    @CsvSource({
        "POST, /api/v1/access/request",
        "POST, /api/v1/access/confirm",
        "GET, /api/v1/access/university",
        "GET, /swagger-ui.html",
        "GET, /swagger-ui/index.html",
        "GET, /v3/api-docs",
        "GET, /v3/api-docs/swagger-config",
        "GET, /actuator/health",
        "GET, /actuator/health/liveness"
    })
    void publicRoutes(String method, String path) {
        assertThat(RouteAccess.of(method, path).isPublic()).isTrue();
    }

    @ParameterizedTest(name = "{0} {1} is only for {2}")
    @CsvSource({
        "POST, /api/v1/admin/universities, PLATFORM_ADMIN",
        "GET, /api/v1/admin/universities, PLATFORM_ADMIN",
        "PUT, /api/v1/admin/universities/UPC/email-domains, PLATFORM_ADMIN",
        "PUT, /api/v1/coordinator/credit-policy, COORDINATOR",
        "GET, /api/v1/coordinator/indicators, COORDINATOR",
        "GET, /api/v1/wallet, STUDENT",
        "POST, /api/v1/bookings, STUDENT",
        "PUT, /api/v1/me/profile, STUDENT",
        "POST, /api/v1/me/academic-record/import, STUDENT",
        "GET, /api/v1/sessions/5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f, STUDENT"
    })
    void routesOfOneRole(String method, String path, AccessRole role) {
        RouteAccess access = RouteAccess.of(method, path);

        assertThat(access.isPublic()).isFalse();
        assertThat(access.allowedRoles()).containsExactly(role);
    }

    @Test
    @DisplayName("a name that only starts like a group is not in it")
    void aPrefixIsAWholeSegment() {
        assertThat(RouteAccess.of("GET", "/api/v1/administrators").allowedRoles())
                .containsExactly(AccessRole.STUDENT);
        assertThat(RouteAccess.of("POST", "/api/v1/access/requests").isPublic()).isFalse();
        assertThat(RouteAccess.of("GET", "/api/v1/access/universities").isPublic()).isFalse();
        assertThat(RouteAccess.of("PUT", "/api/v1/university").allowedRoles())
                .containsExactly(AccessRole.STUDENT);
    }

    @Test
    @DisplayName("the own profile, the own university and the notices are for whoever belongs to a university")
    void membersRoutes() {
        assertThat(RouteAccess.of("GET", "/api/v1/me").allowedRoles())
                .containsExactlyInAnyOrder(AccessRole.STUDENT, AccessRole.COORDINATOR);
        assertThat(RouteAccess.of("GET", "/api/v1/university").allowedRoles())
                .containsExactlyInAnyOrder(AccessRole.STUDENT, AccessRole.COORDINATOR);
        assertThat(RouteAccess.of("GET", "/api/v1/university").allows(AccessRole.PLATFORM_ADMIN))
                .isFalse();
        assertThat(RouteAccess.of("GET", "/api/v1/notifications").allowedRoles())
                .containsExactlyInAnyOrder(AccessRole.STUDENT, AccessRole.COORDINATOR);
    }

    @Test
    @DisplayName("signing out and what lies outside the API need a session of any kind")
    void anySession() {
        RouteAccess signOut = RouteAccess.of("POST", "/api/v1/access/logout");
        RouteAccess info = RouteAccess.of("GET", "/actuator/info");

        assertThat(signOut.isPublic()).isFalse();
        assertThat(info.isPublic()).isFalse();
        for (AccessRole role : AccessRole.values()) {
            assertThat(signOut.allows(role)).isTrue();
            assertThat(info.allows(role)).isTrue();
        }
    }
}
