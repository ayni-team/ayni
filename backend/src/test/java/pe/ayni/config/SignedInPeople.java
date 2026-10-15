package pe.ayni.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * Tests only: a university and the people of it who signed in, each with a real session token.
 *
 * <p>For the tests written after TS03, which set {@code ayni.test.identity-headers=false}: every
 * request carries {@code Authorization: Bearer} and goes through the real access filter, which
 * reads the session, the person and their role from identity's tables. The rows are written as the
 * fixtures of any test are, by the owner of the tables (see {@link FixtureOwnerTestDataSource}), so
 * how the person signed in is not what the test is about.
 */
public final class SignedInPeople {

  private final TenantRepository tenants;
  private final UserRepository users;
  private final UserSessionRepository userSessions;
  private final AccessTokenGenerator tokens;

  public SignedInPeople(
      TenantRepository tenants,
      UserRepository users,
      UserSessionRepository userSessions,
      AccessTokenGenerator tokens) {
    this.tenants = tenants;
    this.users = users;
    this.userSessions = userSessions;
    this.tokens = tokens;
  }

  /** Somebody of a university, the token they sign in with, and the university's code. */
  public record Person(UUID id, String tenantId, String token) {}

  /** A new active university in Lima, with a code nobody else uses. */
  public String aUniversity(String prefix) {
    String code = prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    tenants.save(
        new Tenant(
            UUID.randomUUID(),
            code,
            "University " + code,
            null,
            null,
            null,
            List.of(code.toLowerCase() + ".edu.pe"),
            new BigDecimal("13.00"),
            ZoneId.of("America/Lima"),
            TenantStatus.ACTIVE,
            Instant.now()));
    return code;
  }

  /** An active student of the university, signed in. */
  public Person aStudent(String tenant) {
    return signedIn(tenant, UserRole.STUDENT, UserStatus.ACTIVE);
  }

  /** An active coordinator of the university, signed in. */
  public Person aCoordinator(String tenant) {
    return signedIn(tenant, UserRole.COORDINATOR, UserStatus.ACTIVE);
  }

  /** A person of the university with that role and status, signed in. */
  public Person signedIn(String tenant, UserRole role, UserStatus status) {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    users.save(
        new User(
            id,
            tenant,
            role,
            id + "@" + tenant.toLowerCase() + ".edu.pe",
            role == UserRole.STUDENT ? "S" + id.toString().substring(0, 8) : null,
            (role == UserRole.STUDENT ? "Student " : "Coordinator ") + id.toString().substring(0, 4),
            null,
            null,
            null,
            null,
            status,
            null,
            now,
            now,
            now));
    return new Person(id, tenant, tokenOf(tenant, id));
  }

  /**
   * A new session token for that person, valid for a month, so a test may move its clock days
   * ahead without the session expiring.
   */
  public String tokenOf(String tenant, UUID userId) {
    String token = tokens.generate().rawToken();
    Instant now = Instant.now();
    userSessions.save(
        new UserSession(
            UUID.randomUUID(), tenant, userId, tokens.hash(token), now.plus(Duration.ofDays(30)), now));
    return token;
  }
}
