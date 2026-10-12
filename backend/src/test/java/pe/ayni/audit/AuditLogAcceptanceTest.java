package pe.ayni.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingCancelled.CancelledBy;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.RecognitionRequested;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionStarted;

/**
 * US48, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US48-audit-log.feature}, with the
 * same names. Every entry is produced by publishing the real event inside a transaction, as the
 * module that owns it would, and the identity of each person is stubbed because identity is another
 * module. Each test works in a university of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = AuditTestDatabase.INSTANCE;

  private static final Instant DAY_ONE = Instant.parse("2026-10-01T10:00:00Z");

  private final UUID coordinator = UUID.randomUUID();
  private final UUID restricted = UUID.randomUUID();
  private final UUID ana = UUID.randomUUID();
  private final UUID luis = UUID.randomUUID();
  private final UUID outsider = UUID.randomUUID();
  private final UUID item = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @MockitoBean private IdentityApi identity;

  private String tenant;

  /** Background: a university with a coordinator, one who is restricted, and two students. */
  @BeforeEach
  void aUniversityWithItsPeople() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              if (id.equals(coordinator) || id.equals(restricted)) {
                return new UserView(id, tenant, UserRole.COORDINATOR, "carla@upc.edu.pe", null, "Carla Rios", null, null, null);
              }
              if (id.equals(ana) || id.equals(luis)) {
                return new UserView(id, tenant, UserRole.STUDENT, id + "@upc.edu.pe", "U2023", "A Student", null, null, null);
              }
              throw new NoSuchElementException("user %s not found".formatted(id));
            });
    when(identity.isActive(any(UUID.class))).thenAnswer(call -> !call.getArgument(0).equals(restricted));
  }

  private void publish(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  private ResultActions log(String forTenant, UUID user, String query) throws Exception {
    return mockMvc.perform(get("/api/v1/coordinator/audit-log" + query).header("X-Tenant-Id", forTenant).header("X-User-Id", user.toString()));
  }

  private String read(UUID user, String query) throws Exception {
    return log(tenant, user, query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  private long rows(String where, Object... args) {
    return jdbc.queryForObject("select count(*) from audit.activity_log where tenant_id = ? and " + where, Long.class, prepend(tenant, args));
  }

  private static Object[] prepend(Object first, Object... rest) {
    Object[] all = new Object[rest.length + 1];
    all[0] = first;
    System.arraycopy(rest, 0, all, 1, rest.length);
    return all;
  }

  @Test
  @DisplayName("Consulting the log")
  void consultingTheLog() throws Exception {
    UUID booking = UUID.randomUUID();
    UUID session = UUID.randomUUID();
    UUID request = UUID.randomUUID();
    publish(new BookingConfirmed(tenant, booking, ana, luis, item, DAY_ONE.plusSeconds(86400), DAY_ONE.plusSeconds(90000), List.of(UUID.randomUUID()), Credits.of(1), DAY_ONE));
    publish(new BookingCancelled(tenant, booking, ana, luis, List.of(), CancelledBy.TUTOR, true, DAY_ONE.plusSeconds(3600)));
    publish(new SessionStarted(tenant, session, UUID.randomUUID(), DAY_ONE.plusSeconds(2 * 86400)));
    publish(new RecognitionRequested(tenant, request, ana, 20, DAY_ONE.plusSeconds(3 * 86400)));
    publish(new RecognitionResolved(tenant, request, ana, true, "Complete", DAY_ONE.plusSeconds(4 * 86400)));
    String other = "O" + UUID.randomUUID().toString().substring(0, 8);
    publish(new RecognitionRequested(other, UUID.randomUUID(), outsider, 20, DAY_ONE));

    // By person: what Ana did, the most recent first. A cancellation by the tutor is the tutor's.
    String byAna = read(coordinator, "?actorId=" + ana);
    assertThat(JsonPath.<List<String>>read(byAna, "$.items[*].action")).containsExactly("RECOGNITION_REQUESTED", "BOOKING_CONFIRMED");
    assertThat(JsonPath.<List<String>>read(byAna, "$.items[*].actorRole")).containsOnly("STUDENT");
    assertThat(JsonPath.<Integer>read(byAna, "$.total")).isEqualTo(2);
    assertThat(JsonPath.<List<String>>read(read(coordinator, "?actorId=" + luis), "$.items[*].action")).containsExactly("BOOKING_CANCELLED");

    // By resource: everything that happened to a booking, with its date and result.
    String byBooking = read(coordinator, "?resourceType=BOOKING&resourceId=" + booking);
    assertThat(JsonPath.<List<String>>read(byBooking, "$.items[*].action")).containsExactly("BOOKING_CANCELLED", "BOOKING_CONFIRMED");
    assertThat(JsonPath.<List<String>>read(byBooking, "$.items[*].occurredAt")).containsExactly("2026-10-01T11:00:00Z", "2026-10-01T10:00:00Z");
    assertThat(JsonPath.<List<String>>read(byBooking, "$.items[*].resourceId")).containsOnly(booking.toString());
    assertThat(JsonPath.<String>read(byBooking, "$.items[0].details.late")).isEqualTo("true");
    String byRequest = read(coordinator, "?resourceType=recognition_request&resourceId=" + request);
    assertThat(JsonPath.<List<String>>read(byRequest, "$.items[*].action")).containsExactly("RECOGNITION_APPROVED", "RECOGNITION_REQUESTED");
    assertThat(JsonPath.<String>read(byRequest, "$.items[0].actorRole")).isEqualTo("COORDINATOR");

    // By period: from is inclusive and to is exclusive.
    String inPeriod = read(coordinator, "?from=2026-10-03T10:00:00Z&to=2026-10-05T10:00:00Z");
    assertThat(JsonPath.<List<String>>read(inPeriod, "$.items[*].action")).containsExactly("RECOGNITION_REQUESTED", "SESSION_STARTED");
    String firstOnly = read(coordinator, "?actorId=" + ana + "&from=2026-10-01T10:00:00Z&to=2026-10-04T10:00:00Z");
    assertThat(JsonPath.<List<String>>read(firstOnly, "$.items[*].action")).containsExactly("BOOKING_CONFIRMED");

    // Pages, and the university of the request is the only one that is read.
    String firstPage = read(coordinator, "?size=2&page=0");
    assertThat(JsonPath.<List<String>>read(firstPage, "$.items[*].action")).hasSize(2);
    assertThat(JsonPath.<Integer>read(firstPage, "$.size")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(firstPage, "$.total")).isGreaterThanOrEqualTo(5);
    assertThat(JsonPath.<List<String>>read(firstPage, "$.items[*].actorId")).doesNotContain(outsider.toString());
    assertThat(rows("actor_id = ?", outsider)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from audit.activity_log where tenant_id = ?", Long.class, other)).isEqualTo(1);
  }

  @Test
  @DisplayName("A log that is not modified")
  void aLogThatIsNotModified() throws Exception {
    UUID booking = UUID.randomUUID();
    publish(new BookingConfirmed(tenant, booking, ana, luis, item, DAY_ONE, DAY_ONE.plusSeconds(3600), List.of(), Credits.of(1), DAY_ONE));
    assertThat(rows("resource_id = ?", booking)).isEqualTo(1);

    assertThatThrownBy(() -> jdbc.update("update audit.activity_log set action = 'NOTHING_HAPPENED' where tenant_id = ?", tenant))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append only");
    assertThatThrownBy(() -> jdbc.update("delete from audit.activity_log where tenant_id = ?", tenant))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append only");
    assertThatThrownBy(() -> jdbc.execute("truncate audit.activity_log")).isInstanceOf(DataAccessException.class).hasMessageContaining("append only");
    assertThat(rows("resource_id = ? and action = 'BOOKING_CONFIRMED'", booking)).isEqualTo(1);

    // What went wrong is corrected by what happened next, not by editing what was written.
    publish(new BookingCancelled(tenant, booking, ana, luis, List.of(), CancelledBy.STUDENT, false, DAY_ONE.plusSeconds(60)));

    String history = read(coordinator, "?resourceType=BOOKING&resourceId=" + booking);
    assertThat(JsonPath.<List<String>>read(history, "$.items[*].action")).containsExactly("BOOKING_CANCELLED", "BOOKING_CONFIRMED");
  }

  @Test
  @DisplayName("Restricted access")
  void restrictedAccess() throws Exception {
    log(tenant, ana, "").andExpect(status().isForbidden());
    log(tenant, restricted, "?size=1").andExpect(status().isForbidden());
    String answer = log(tenant, outsider, "").andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
    assertThat(JsonPath.<Integer>read(answer, "$.status")).isEqualTo(403);
    assertThat(JsonPath.<String>read(answer, "$.path")).isEqualTo("/api/v1/coordinator/audit-log");

    // The refusal did not roll its own record back: each attempt is a row, with who and in what role.
    Map<UUID, String> roles = new java.util.HashMap<>();
    jdbc.query(
        "select actor_id, actor_role from audit.activity_log where tenant_id = ? and action = 'AUDIT_LOG_ACCESS_DENIED'",
        rs -> {
          roles.put(UUID.fromString(rs.getString("actor_id")), rs.getString("actor_role"));
        },
        tenant);
    assertThat(roles).containsOnly(Map.entry(ana, "STUDENT"), Map.entry(restricted, "COORDINATOR"), Map.entry(outsider, "UNKNOWN"));

    // And the coordinator can read it afterwards, like anything else.
    String attempts = read(coordinator, "?resourceType=AUDIT_LOG");
    assertThat(JsonPath.<List<String>>read(attempts, "$.items[*].actorId")).containsExactlyInAnyOrder(ana.toString(), restricted.toString(), outsider.toString());

    // Without a person there is nobody to refuse: the request is malformed and nothing is written.
    mockMvc.perform(get("/api/v1/coordinator/audit-log").header("X-Tenant-Id", tenant)).andExpect(status().isBadRequest());
    assertThat(rows("action = 'AUDIT_LOG_ACCESS_DENIED'")).isEqualTo(3);
  }

  @Test
  @DisplayName("A failure to record does not undo what was published")
  void aFailureToRecordDoesNotUndoWhatWasPublished() throws Exception {
    String tooLong = "T" + "x".repeat(40);

    assertThatCode(() -> publish(new SessionStarted(tooLong, UUID.randomUUID(), UUID.randomUUID(), DAY_ONE))).doesNotThrowAnyException();

    assertThat(jdbc.queryForObject("select count(*) from audit.activity_log where tenant_id = ?", Long.class, tooLong)).isZero();
    // And the log goes on recording what comes next.
    UUID session = UUID.randomUUID();
    publish(new SessionCompleted(tenant, session, UUID.randomUUID(), luis, ana, item, Credits.of(1), DAY_ONE));
    assertThat(rows("resource_id = ? and action = 'SESSION_COMPLETED'", session)).isEqualTo(1);
  }
}
