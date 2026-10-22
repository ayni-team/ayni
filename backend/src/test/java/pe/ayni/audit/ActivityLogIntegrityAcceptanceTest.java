package pe.ayni.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.audit.domain.model.ActorRole;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.SessionStarted;

/**
 * US48, the chain of the activity log, over HTTP with real session tokens (TS03) against a real
 * PostgreSQL. "Detecting tampering" of {@code features/US48-audit-log.feature} runs here with the
 * same name; the rules of the chain are in {@code ActivityChainTest}, and the entries written at
 * once in {@link ActivityLogChainConcurrencyTest}.
 *
 * <p>Every entry is produced by publishing the real event inside a transaction, as sessions does:
 * {@code SessionStarted}, which nobody but audit hears. Each test has a university of its own, so
 * one test's chain, and the rows it damages, never reach another's.
 *
 * <p>A row is damaged the way only the owner of the database could: the append only trigger is
 * switched off for that one statement and switched on again straight away, with the connection of
 * the owner that every test fixture uses (see {@code FixtureOwnerTestDataSource}). The application's
 * role cannot do this. The tests of a module run one after another against one container, so no
 * other test writes the log while the trigger is off.
 *
 * <p>The page of the verification is three entries long, so every chain of a few entries crosses a
 * page boundary.
 */
@SpringBootTest(properties = {"ayni.test.identity-headers=false", "ayni.audit.integrity-page-size=3"})
@AutoConfigureMockMvc
class ActivityLogIntegrityAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = AuditTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
  private static final Instant DAY_ONE = Instant.parse("2026-10-01T10:00:00Z");
  private static final String INTEGRITY = "/api/v1/coordinator/audit-log/integrity";

  /** The clock the application reads, so the moment of the check is known. */
  @TestConfiguration(proxyBeanMethods = false)
  static class FixedTime {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  private SignedInPeople people;
  private String tenant;
  private Person coordinator;

  /** Background: a university with an active coordinator, signed in. */
  @BeforeEach
  void aUniversityWithItsCoordinator() {
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("H");
    coordinator = people.aCoordinator(tenant);
  }

  @Test
  @DisplayName("Detecting tampering")
  void detectingTampering() throws Exception {
    // Given the entries of the log are chained in sequence
    sessionsStarted(tenant, 8);
    assertThat(integrity(coordinator).andReturn().getResponse().getContentAsString()).contains("\"intact\":true");

    // When one of them was altered behind the application: the fifth now tells another fact. The
    // sixth still carries the hash the fifth had, so it is not the one pointed out.
    UUID fifth = entryAt(tenant, 5);
    behindTheTrigger("update audit.activity_log set action = 'SESSION_COMPLETED' where id = ?", fifth);

    // Then the entry that does not fit the sequence is pointed out
    String body =
        integrity(coordinator)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intact").value(false))
            .andExpect(jsonPath("$.firstInconsistentEntryId").value(fifth.toString()))
            .andExpect(jsonPath("$.firstInconsistentOccurredAt").value(DAY_ONE.plus(Duration.ofMinutes(4)).toString()))
            .andExpect(jsonPath("$.firstInconsistentSequenceNumber").value(5))
            .andExpect(jsonPath("$.entriesChecked").value(5))
            .andExpect(jsonPath("$.checkedAt").value(NOW.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Where the chain breaks, and nothing of what the entry says.
    assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet())
        .containsExactlyInAnyOrder(
            "intact",
            "entriesChecked",
            "checkedAt",
            "firstInconsistentEntryId",
            "firstInconsistentOccurredAt",
            "firstInconsistentSequenceNumber");
  }

  @Test
  @DisplayName("A log nobody touched is intact, however many pages it takes")
  void aLogNobodyTouchedIsIntact() throws Exception {
    sessionsStarted(tenant, 8);

    String body =
        integrity(coordinator)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intact").value(true))
            .andExpect(jsonPath("$.entriesChecked").value(8))
            .andExpect(jsonPath("$.checkedAt").value(NOW.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet())
        .containsExactlyInAnyOrder("intact", "entriesChecked", "checkedAt");
    // Each entry took the next place and the hash of the one before it.
    assertThat(jdbc.queryForList("select sequence_number from audit.activity_log where tenant_id = ? order by sequence_number", Long.class, tenant))
        .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    assertThat(jdbc.queryForObject("select previous_hash from audit.activity_log where id = ?", String.class, entryAt(tenant, 1))).isNull();
    assertThat(jdbc.queryForObject("select previous_hash from audit.activity_log where id = ?", String.class, entryAt(tenant, 2)))
        .isEqualTo(jdbc.queryForObject("select entry_hash from audit.activity_log where id = ?", String.class, entryAt(tenant, 1)));
  }

  @Test
  @DisplayName("A university without entries has nothing to break")
  void aUniversityWithoutEntriesIsIntact() throws Exception {
    integrity(coordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intact").value(true))
        .andExpect(jsonPath("$.entriesChecked").value(0));
  }

  @Test
  @DisplayName("A link broken at the start of a page is found")
  void aLinkBrokenAtTheStartOfAPageIsFound() throws Exception {
    sessionsStarted(tenant, 8);
    // The fourth entry opens the second page: it can only be judged against the last entry of the
    // first one, which the verification keeps for that.
    UUID fourth = entryAt(tenant, 4);
    behindTheTrigger("update audit.activity_log set previous_hash = repeat('0', 64) where id = ?", fourth);

    integrity(coordinator)
        .andExpect(jsonPath("$.intact").value(false))
        .andExpect(jsonPath("$.firstInconsistentEntryId").value(fourth.toString()))
        .andExpect(jsonPath("$.entriesChecked").value(4));
  }

  @Test
  @DisplayName("A removed entry is found at the entry that followed it")
  void aRemovedEntryIsFoundAtTheEntryThatFollowedIt() throws Exception {
    sessionsStarted(tenant, 8);
    UUID fifth = entryAt(tenant, 5);
    behindTheTrigger("delete from audit.activity_log where id = ?", entryAt(tenant, 4));

    // The fifth is the first that points at an entry no longer there. It is the fourth read, while
    // its place still says five: the gap is the proof.
    integrity(coordinator)
        .andExpect(jsonPath("$.intact").value(false))
        .andExpect(jsonPath("$.firstInconsistentEntryId").value(fifth.toString()))
        .andExpect(jsonPath("$.firstInconsistentSequenceNumber").value(5))
        .andExpect(jsonPath("$.entriesChecked").value(4));
  }

  @Test
  @DisplayName("An entry slipped in out of order is found, even one that hashes correctly")
  void anEntrySlippedInOutOfOrderIsFound() throws Exception {
    sessionsStarted(tenant, 8);

    // A forger who knows the format writes an entry that hashes correctly, takes the next free
    // place and points at the third entry, as if it had been written right after it. Inserting
    // needs no trigger switched off: the table only refuses changes.
    ActivityEntry forged =
        ActivityEntry.of(UUID.randomUUID(), tenant, null, ActorRole.SYSTEM, "RECOGNITION_APPROVED", "RECOGNITION_REQUEST", UUID.randomUUID(), null, DAY_ONE.plusSeconds(150));
    forged.chainAfter(8, jdbc.queryForObject("select entry_hash from audit.activity_log where id = ?", String.class, entryAt(tenant, 3)));
    jdbc.update(
        """
        insert into audit.activity_log
          (id, tenant_id, actor_id, actor_role, action, resource_type, resource_id, metadata,
           occurred_at, sequence_number, previous_hash, entry_hash)
        values (?, ?, null, ?, ?, ?, ?, null, ?, ?, ?, ?)
        """,
        forged.getId(),
        tenant,
        forged.getActorRole().name(),
        forged.getAction(),
        forged.getResourceType(),
        forged.getResourceId(),
        Timestamp.from(forged.getOccurredAt()),
        forged.getSequenceNumber(),
        forged.getPreviousHash(),
        forged.getEntryHash());

    integrity(coordinator)
        .andExpect(jsonPath("$.intact").value(false))
        .andExpect(jsonPath("$.firstInconsistentEntryId").value(forged.getId().toString()))
        .andExpect(jsonPath("$.firstInconsistentOccurredAt").value(DAY_ONE.plusSeconds(150).toString()))
        .andExpect(jsonPath("$.firstInconsistentSequenceNumber").value(9))
        .andExpect(jsonPath("$.entriesChecked").value(9));
  }

  @Test
  @DisplayName("Entries from before the chain are not reported as altered")
  void entriesFromBeforeTheChainAreNotReported() throws Exception {
    // Given entries written before the log was chained: they carry no hash and no place.
    for (int i = 0; i < 3; i++) {
      entryWithoutHash(tenant);
    }

    // The chain starts at the first entry written since.
    sessionsStarted(tenant, 4);
    UUID first = entryAt(tenant, 1);
    assertThat(jdbc.queryForObject("select previous_hash from audit.activity_log where id = ?", String.class, first)).isNull();

    integrity(coordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intact").value(true))
        .andExpect(jsonPath("$.entriesChecked").value(4));
    assertThat(jdbc.queryForObject("select count(*) from audit.activity_log where tenant_id = ? and entry_hash is null", Long.class, tenant))
        .isEqualTo(3);
  }

  @Test
  @DisplayName("The chain of another university is neither read nor mixed in")
  void theChainOfAnotherUniversityIsNotMixedIn() throws Exception {
    String other = people.aUniversity("J");
    Person otherCoordinator = people.aCoordinator(other);
    // Written one after another, alternating: each university keeps a chain of its own.
    for (int i = 0; i < 4; i++) {
      sessionsStarted(tenant, 1);
      sessionsStarted(other, 1);
    }
    assertThat(jdbc.queryForList("select sequence_number from audit.activity_log where tenant_id = ? order by sequence_number", Long.class, other))
        .containsExactly(1L, 2L, 3L, 4L);
    behindTheTrigger("update audit.activity_log set resource_id = ? where id = ?", UUID.randomUUID(), entryAt(tenant, 2));

    // The damage in one does not reach the other, and the other's entries are not counted.
    integrity(coordinator)
        .andExpect(jsonPath("$.intact").value(false))
        .andExpect(jsonPath("$.firstInconsistentSequenceNumber").value(2));
    integrity(otherCoordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intact").value(true))
        .andExpect(jsonPath("$.entriesChecked").value(4));
  }

  @Test
  @DisplayName("A student cannot verify the log")
  void aStudentCannotVerifyTheLog() throws Exception {
    // Access control refuses the role before the use case runs, as for the log itself, so there is
    // no person of the university to write the attempt down for (TS03).
    integrity(people.aStudent(tenant))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.path").value(INTEGRITY));
  }

  @Test
  @DisplayName("A coordinator who is restricted cannot verify the log, and the attempt is recorded")
  void aRestrictedCoordinatorCannotVerifyTheLog() throws Exception {
    Person restricted = people.signedIn(tenant, UserRole.COORDINATOR, UserStatus.RESTRICTED);

    integrity(restricted)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.path").value(INTEGRITY))
        .andExpect(jsonPath("$.message").exists());

    // The refusal did not roll its own record back, and the record is chained like any other.
    List<Map<String, Object>> attempts =
        jdbc.queryForList(
            "select actor_id, actor_role, resource_type, metadata ->> 'reason' as reason, sequence_number, entry_hash from audit.activity_log where tenant_id = ? and action = 'AUDIT_LOG_ACCESS_DENIED'",
            tenant);
    assertThat(attempts).hasSize(1);
    assertThat(attempts.getFirst())
        .containsEntry("actor_id", restricted.id())
        .containsEntry("actor_role", "COORDINATOR")
        .containsEntry("resource_type", "AUDIT_LOG")
        .containsEntry("reason", "NOT_ACTIVE")
        .containsEntry("sequence_number", 1L);
    assertThat(attempts.getFirst().get("entry_hash")).isNotNull();
    integrity(coordinator).andExpect(jsonPath("$.intact").value(true)).andExpect(jsonPath("$.entriesChecked").value(1));
  }

  @Test
  @DisplayName("Nobody without a session can verify the log")
  void nobodyWithoutASessionCanVerifyTheLog() throws Exception {
    mockMvc.perform(get(INTEGRITY)).andExpect(status().isUnauthorized());
  }

  private ResultActions integrity(Person who) throws Exception {
    return mockMvc.perform(get(INTEGRITY).header("Authorization", "Bearer " + who.token()));
  }

  /** Sessions start in that university, one minute apart: one entry each. */
  private void sessionsStarted(String university, int entries) {
    long already = jdbc.queryForObject("select count(*) from audit.activity_log where tenant_id = ? and entry_hash is not null", Long.class, university);
    for (int i = 0; i < entries; i++) {
      Instant at = DAY_ONE.plus(Duration.ofMinutes(already + i));
      transactions.executeWithoutResult(
          status -> events.publishEvent(new SessionStarted(university, UUID.randomUUID(), UUID.randomUUID(), at)));
    }
  }

  private UUID entryAt(String university, long sequenceNumber) {
    return jdbc.queryForObject(
        "select id from audit.activity_log where tenant_id = ? and sequence_number = ?", UUID.class, university, sequenceNumber);
  }

  /** An entry as they were written before the log was chained. */
  private void entryWithoutHash(String university) {
    jdbc.update(
        """
        insert into audit.activity_log
          (id, tenant_id, actor_id, actor_role, action, resource_type, resource_id, metadata, occurred_at)
        values (?, ?, null, 'SYSTEM', 'SESSION_COMPLETED', 'SESSION', ?, '{}'::jsonb, ?)
        """,
        UUID.randomUUID(),
        university,
        UUID.randomUUID(),
        Timestamp.from(DAY_ONE.minus(Duration.ofDays(1))));
  }

  /**
   * Runs one statement against the log as only its owner could: with the append only trigger off.
   * The trigger is back on before this returns, whatever the statement did.
   */
  private void behindTheTrigger(String statement, Object... arguments) {
    jdbc.execute("alter table audit.activity_log disable trigger tr_activity_log_append_only");
    try {
      jdbc.update(statement, arguments);
    } finally {
      jdbc.execute("alter table audit.activity_log enable trigger tr_activity_log_append_only");
    }
  }
}
