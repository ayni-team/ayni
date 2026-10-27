package pe.ayni.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.application.ExpireCredits;

/**
 * TS02, over HTTP against a real PostgreSQL with real session tokens: the verification of the
 * ledger chain and the count of balances the history does not explain.
 *
 * <p>The scenario of {@code features/TS02-ledger-integrity.feature} that is verified here is
 * "Detection of tampering"; the other three run in {@link WalletLedgerTest}. Each test has a
 * university of its own, so one test's chain, and the rows it damages, never reach another's.
 *
 * <p>A row is damaged the way only the owner of the database could: the append only trigger is
 * switched off for the length of that one statement and switched on again straight away, with the
 * connection of the owner that every test fixture uses (see {@code FixtureOwnerTestDataSource}).
 * The application's role cannot do this, and neither can anyone going through the trigger. The
 * tests of a module run one after another against one container, so no other test writes the
 * ledger while the trigger is off.
 *
 * <p>The page of the verification is three entries long, so every chain of a few entries crosses
 * a page boundary.
 */
@SpringBootTest(
    properties = {"ayni.test.identity-headers=false", "ayni.wallet.integrity-page-size=3"})
@AutoConfigureMockMvc
class LedgerIntegrityAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = WalletTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

  /** Moves the clock the application reads, so a test can let credits reach their expiry. */
  @TestConfiguration(proxyBeanMethods = false)
  static class FixedTime {

    @Bean
    @Primary
    MovableClock movableClock() {
      return new MovableClock(NOW);
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private WalletApi wallet;
  @Autowired private ExpireCredits expireCredits;
  @Autowired private MovableClock clock;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  private SignedInPeople people;
  private String tenant;
  private Person coordinator;

  /** A university with an active coordinator, and a clock set back to the same instant. */
  @BeforeEach
  void aUniversityWithItsCoordinator() {
    clock.set(NOW);
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("L");
    coordinator = people.aCoordinator(tenant);
  }

  @Test
  @DisplayName("Detection of tampering")
  void detectionOfTampering() throws Exception {

    grantEightTimes(tenant);
    // The fifth entry, changed behind the application's back: its contents no longer match its
    // hash. The sixth carries the hash the fifth had, so it still points at the right place and
    // is not the one reported.
    UUID fifth = entryAt(tenant, 5);
    behindTheTrigger("update wallet.ledger_entries set amount = 999 where id = ?", fifth);

    String body =
        integrity(coordinator)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intact").value(false))
            .andExpect(jsonPath("$.firstInconsistentEntryId").value(fifth.toString()))
            .andExpect(jsonPath("$.firstInconsistentPosition").value(5))
            .andExpect(jsonPath("$.firstInconsistentSequenceNumber").value(5))
            .andExpect(jsonPath("$.entriesChecked").value(5))
            .andExpect(jsonPath("$.checkedAt").value(NOW.toString()))
            // The same row also makes one student's balance impossible to rebuild.
            .andExpect(jsonPath("$.balanceMismatches").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Where the chain breaks and nothing about whose credits they were or how many.
    assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet())
        .containsExactlyInAnyOrder(
            "intact",
            "entriesChecked",
            "checkedAt",
            "firstInconsistentEntryId",
            "firstInconsistentPosition",
            "firstInconsistentSequenceNumber",
            "balanceMismatches");
  }

  @Test
  @DisplayName("A link broken at the start of a page is found")
  void aLinkBrokenAtTheStartOfAPageIsFound() throws Exception {

    grantEightTimes(tenant);
    // The fourth entry opens the second page: it can only be judged against the last entry of the
    // first one, which the verification keeps for that.
    UUID fourth = entryAt(tenant, 4);
    behindTheTrigger(
        "update wallet.ledger_entries set previous_hash = repeat('0', 64) where id = ?", fourth);

    integrity(coordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intact").value(false))
        .andExpect(jsonPath("$.firstInconsistentEntryId").value(fourth.toString()))
        .andExpect(jsonPath("$.firstInconsistentPosition").value(4));
  }

  @Test
  @DisplayName("A deleted entry is found at the entry that followed it")
  void aDeletedEntryIsFoundAtTheEntryThatFollowedIt() throws Exception {

    grantEightTimes(tenant);
    UUID fourth = entryAt(tenant, 4);
    UUID fifth = entryAt(tenant, 5);
    behindTheTrigger("delete from wallet.ledger_entries where id = ?", fourth);

    // The fifth is the first that points at an entry no longer there. Its position is the fourth
    // of the chain as it stands, while its sequence number still says five: the gap is the proof.
    integrity(coordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intact").value(false))
        .andExpect(jsonPath("$.firstInconsistentEntryId").value(fifth.toString()))
        .andExpect(jsonPath("$.firstInconsistentPosition").value(4))
        .andExpect(jsonPath("$.firstInconsistentSequenceNumber").value(5))
        .andExpect(jsonPath("$.entriesChecked").value(4));
  }

  @Test
  @DisplayName("A ledger nobody touched is intact, however many pages it takes")
  void aLedgerNobodyTouchedIsIntact() throws Exception {

    grantEightTimes(tenant);

    String body =
        integrity(coordinator)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intact").value(true))
            .andExpect(jsonPath("$.entriesChecked").value(8))
            .andExpect(jsonPath("$.checkedAt").value(NOW.toString()))
            .andExpect(jsonPath("$.balanceMismatches").value(0))
            .andExpect(jsonPath("$.firstInconsistentEntryId").doesNotExist())
            .andExpect(jsonPath("$.firstInconsistentPosition").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(JsonPath.<Map<String, Object>>read(body, "$").keySet())
        .containsExactlyInAnyOrder("intact", "entriesChecked", "checkedAt", "balanceMismatches");
  }

  @Test
  @DisplayName("A university without movements has nothing to break")
  void aUniversityWithoutMovementsIsIntact() throws Exception {

    integrity(coordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intact").value(true))
        .andExpect(jsonPath("$.entriesChecked").value(0))
        .andExpect(jsonPath("$.balanceMismatches").value(0));
  }

  @Test
  @DisplayName("The chain of another university is neither read nor mixed in")
  void theChainOfAnotherUniversityIsNotMixedIn() throws Exception {

    String other = people.aUniversity("M");
    Person otherCoordinator = people.aCoordinator(other);
    grantEightTimes(tenant);
    grant(other, 2);
    behindTheTrigger(
        "update wallet.ledger_entries set amount = 999 where id = ?", entryAt(tenant, 2));

    // Each university is judged by its own entries: the damage in one does not reach the other, and
    // the other's two entries are not counted among the eight.
    integrity(coordinator)
        .andExpect(jsonPath("$.intact").value(false))
        .andExpect(jsonPath("$.firstInconsistentPosition").value(2));
    integrity(otherCoordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intact").value(true))
        .andExpect(jsonPath("$.entriesChecked").value(2))
        .andExpect(jsonPath("$.balanceMismatches").value(0));
  }

  @Test
  @DisplayName("A student cannot verify the ledger")
  void aStudentCannotVerifyTheLedger() throws Exception {

    integrity(people.aStudent(tenant))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.path").value("/api/v1/coordinator/ledger/integrity"));
  }

  @Test
  @DisplayName("A coordinator who is restricted cannot verify the ledger")
  void aRestrictedCoordinatorCannotVerifyTheLedger() throws Exception {

    Person restricted = people.signedIn(tenant, UserRole.COORDINATOR, UserStatus.RESTRICTED);

    integrity(restricted)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  @DisplayName("Nobody without a session can verify the ledger")
  void nobodyWithoutASessionCanVerifyTheLedger() throws Exception {

    mockMvc.perform(get("/api/v1/coordinator/ledger/integrity")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("An account whose history does not add up to its balance is counted, not named")
  void anAccountThatDoesNotAddUpIsCounted() throws Exception {

    grantEightTimes(tenant);
    // Credits that left a group without a movement behind them: the chain is untouched, and the
    // balance can no longer be rebuilt from it. The lots carry no trigger, so no tool is needed.
    jdbc.update(
        """
        update wallet.credit_lots set remaining_amount = remaining_amount - 1
        where id = (select id from wallet.credit_lots where tenant_id = ? limit 1)
        """,
        tenant);

    integrity(coordinator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intact").value(true))
        .andExpect(jsonPath("$.balanceMismatches").value(1));
  }

  @Test
  @DisplayName("Credits that expired and are not yet written down do not count as a mismatch")
  void creditsThatExpiredAndAreNotYetWrittenDownAreNotAMismatch() throws Exception {

    UUID student = people.aStudent(tenant).id();
    TenantContext.runAs(
        tenant,
        () ->
            wallet.grant(
                student, Credits.of(5), CreditType.SEED, NOW.plus(Duration.ofDays(10)), UUID.randomUUID()));

    // The balance already ignores the group and the history still counts it, until the job of the
    // night writes the expiry down. In between nothing is wrong.
    clock.set(NOW.plus(Duration.ofDays(11)));
    integrity(coordinator).andExpect(jsonPath("$.balanceMismatches").value(0));

    TenantContext.runAs(tenant, () -> expireCredits.forCurrentUniversity());
    integrity(coordinator)
        .andExpect(jsonPath("$.intact").value(true))
        .andExpect(jsonPath("$.entriesChecked").value(2))
        .andExpect(jsonPath("$.balanceMismatches").value(0));
  }

  private ResultActions integrity(Person who) throws Exception {
    return mockMvc.perform(
        get("/api/v1/coordinator/ledger/integrity").header("Authorization", "Bearer " + who.token()));
  }

  private void grantEightTimes(String university) {
    grant(university, 8);
  }

  /** One entry each, from eight students, so every account has a single movement. */
  private void grant(String university, int entries) {
    for (int i = 0; i < entries; i++) {
      UUID student = people.aStudent(university).id();
      TenantContext.runAs(
          university,
          () ->
              wallet.grant(student, Credits.of(1), CreditType.EARNED, null, UUID.randomUUID()));
    }
  }

  private UUID entryAt(String university, long sequenceNumber) {
    return jdbc.queryForObject(
        "select id from wallet.ledger_entries where tenant_id = ? and sequence_number = ?",
        UUID.class,
        university,
        sequenceNumber);
  }

  /**
   * Runs one statement against the ledger as only its owner could: with the append only trigger
   * off. The trigger is back on before this returns, whatever the statement did.
   */
  private void behindTheTrigger(String statement, Object... arguments) {
    jdbc.execute("alter table wallet.ledger_entries disable trigger tr_ledger_entries_append_only");
    try {
      jdbc.update(statement, arguments);
    } finally {
      jdbc.execute("alter table wallet.ledger_entries enable trigger tr_ledger_entries_append_only");
    }
  }

  /** A clock a test moves, so credits reach their expiry without waiting. */
  static final class MovableClock extends Clock {

    private final AtomicReference<Instant> now;

    MovableClock(Instant start) {
      this.now = new AtomicReference<>(start);
    }

    void set(Instant instant) {
      now.set(instant);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }
}
