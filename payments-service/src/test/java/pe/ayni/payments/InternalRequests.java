package pe.ayni.payments;

import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Tests only: a call as the monolith forwards it, with the secret, the university and the person. */
final class InternalRequests {

  static final String TOKEN = "test-internal-token";

  private InternalRequests() {}

  static MockHttpServletRequestBuilder asMonolith(
      MockHttpServletRequestBuilder request, String tenantId, UUID userId) {
    MockHttpServletRequestBuilder forwarded = request.header("X-Internal-Token", TOKEN);
    if (tenantId != null) {
      forwarded = forwarded.header("X-Tenant-Id", tenantId);
    }
    if (userId != null) {
      forwarded = forwarded.header("X-User-Id", userId);
    }
    return forwarded;
  }
}
