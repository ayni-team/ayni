package pe.ayni.sessions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Builds what the whiteboard scenarios need: a university, two students who signed in, and a
 * session between them already in progress, both of them checked in.
 *
 * <p>The session is written straight in its tables: how it came to be in progress is US09's
 * business, and these scenarios are about what happens on its board. The people sign in with a
 * real session token, so every request goes through the real access filter (TS03).
 */
class WhiteboardScenario {

  /** A session between two people of a university, with the token each of them holds. */
  record Pair(
      String tenantId,
      UUID sessionId,
      UUID student,
      String studentToken,
      UUID tutor,
      String tutorToken) {}

  private final JdbcTemplate jdbc;
  private final TenantRepository tenants;
  private final UserRepository users;
  private final UserSessionRepository userSessions;
  private final AccessTokenGenerator tokens;

  WhiteboardScenario(
      JdbcTemplate jdbc,
      TenantRepository tenants,
      UserRepository users,
      UserSessionRepository userSessions,
      AccessTokenGenerator tokens) {
    this.jdbc = jdbc;
    this.tenants = tenants;
    this.users = users;
    this.userSessions = userSessions;
    this.tokens = tokens;
  }

  String aUniversity() {
    String code = "W" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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

  UUID aStudent(String tenant) {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    users.save(
        new User(
            id,
            tenant,
            UserRole.STUDENT,
            id + "@" + tenant.toLowerCase() + ".edu.pe",
            "S" + id.toString().substring(0, 8),
            "Student " + id.toString().substring(0, 4),
            null,
            null,
            null,
            null,
            UserStatus.ACTIVE,
            null,
            now,
            now,
            now));
    return id;
  }

  String tokenOf(String tenant, UUID userId) {
    String token = tokens.generate().rawToken();
    Instant now = Instant.now();
    userSessions.save(
        new UserSession(
            UUID.randomUUID(), tenant, userId, tokens.hash(token), now.plus(Duration.ofDays(1)), now));
    return token;
  }

  /** A session in progress in a new university. */
  Pair inProgress() {
    return inProgress(aUniversity());
  }

  Pair inProgress(String tenant) {
    UUID student = aStudent(tenant);
    UUID tutor = aStudent(tenant);
    UUID session = aSession(tenant, student, tutor, "IN_PROGRESS");
    for (UUID participant : List.of(student, tutor)) {
      jdbc.update(
          """
          insert into sessions.participations (id, tenant_id, session_id, user_id, role, joined_at)
          values (?, ?, ?, ?, ?, now())
          """,
          UUID.randomUUID(),
          tenant,
          session,
          participant,
          participant.equals(student) ? "STUDENT" : "TUTOR");
    }
    return new Pair(
        tenant, session, student, tokenOf(tenant, student), tutor, tokenOf(tenant, tutor));
  }

  /** A session between two students in the given state, started an hour ago. */
  UUID aSession(String tenant, UUID student, UUID tutor, String status) {
    UUID session = UUID.randomUUID();
    Instant start = Instant.now().minus(Duration.ofMinutes(20));
    jdbc.update(
        """
        insert into sessions.sessions
          (id, tenant_id, booking_id, student_id, tutor_id, scheduled_start, scheduled_end,
           room_name, started_at, status)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        session,
        tenant,
        UUID.randomUUID(),
        student,
        tutor,
        java.sql.Timestamp.from(start),
        java.sql.Timestamp.from(start.plus(Duration.ofHours(1))),
        "room-" + session,
        "IN_PROGRESS".equals(status) || "COMPLETED".equals(status) || "UNVERIFIED".equals(status)
            ? java.sql.Timestamp.from(start)
            : null,
        status);
    return session;
  }

  /** The content stored for the board of a session, as the database has it. */
  java.util.Map<String, Object> storedBoard(UUID sessionId) {
    return jdbc.queryForMap(
        "select content::text as content, has_activity from sessions.whiteboards"
            + " where session_id = ?",
        sessionId);
  }

  String statusOf(UUID sessionId) {
    return jdbc.queryForObject(
        "select status from sessions.sessions where id = ?", String.class, sessionId);
  }
}
