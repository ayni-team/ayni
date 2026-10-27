package pe.ayni.config.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * Tests only: a platform administrator who signed in, with a real session token, as {@code
 * SignedInPeople} gives the people of a university.
 */
final class DemoAdministrators {

  private DemoAdministrators() {}

  /**
   * A new active administrator and the token of a session that lasts a year, as the sessions of
   * the demo profile do: a month ahead would expire one of thirty days.
   */
  static String aSignedInAdministrator(
      PlatformAdminRepository admins, UserSessionRepository sessions, AccessTokenGenerator tokens) {
    UUID adminId = UUID.randomUUID();
    Instant now = Instant.now();
    admins.save(
        new PlatformAdmin(
            adminId, "admin-" + adminId + "@ayni.test", "Admin", PlatformAdminStatus.ACTIVE, now));
    String token = tokens.generate().rawToken();
    sessions.save(
        UserSession.forPlatformAdmin(
            UUID.randomUUID(), adminId, tokens.hash(token), now.plus(Duration.ofDays(365)), now));
    return token;
  }
}
