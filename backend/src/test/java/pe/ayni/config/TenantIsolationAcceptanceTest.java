package pe.ayni.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.application.ListUniversitiesQuery;
import pe.ayni.identity.application.UniversityAdminView;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;
import pe.ayni.wallet.application.ExpireCredits;

/**
 * TS01 against a real PostgreSQL, connected as the application's role. One test per scenario of
 * {@code features/TS01-row-level-security.feature}, with the same name.
 *
 * <p>The fixtures are written as the owner, by {@link FixtureOwnerTestDataSource}, so they can
 * prepare two universities at once; what each scenario proves is read with the application's role,
 * through the application or through {@link FixtureOwnerTestDataSource#asApplication}.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@Import(TenancyTestConfig.class)
class TenantIsolationAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = TenancyTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transactions;
  @Autowired private TenancyMutableClock clock;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository sessions;
  @Autowired private AccessTokenGenerator tokens;
  @Autowired private WalletApi wallet;
  @Autowired private ExpireCredits expireCredits;
  @Autowired private ListUniversitiesQuery listUniversities;

  private String first;
  private String second;

  @BeforeEach
  void twoUniversities() {
    first = aUniversity();
    second = aUniversity();
  }

  private String aUniversity() {
    String code = "R" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
            clock.instant()));
    return code;
  }

  private UUID aPerson(String tenant, UserRole role) {
    UUID id = UUID.randomUUID();
    Instant now = clock.instant();
    users.save(
        new User(
            id,
            tenant,
            role,
            id + "@" + tenant.toLowerCase() + ".edu.pe",
            role == UserRole.STUDENT ? "S" + id.toString().substring(0, 8) : null,
            "Person of " + tenant,
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

  private UUID anEntryInTheLogOf(String tenant) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into audit.activity_log
          (id, tenant_id, actor_id, actor_role, action, resource_type, resource_id, metadata,
           occurred_at)
        values (?, ?, null, 'SYSTEM', 'SESSION_COMPLETED', 'SESSION', ?, '{}'::jsonb, now())
        """,
        id,
        tenant,
        UUID.randomUUID());
    return id;
  }

  private String sessionTokenOf(String tenant, UUID userId) {
    String token = tokens.generate().rawToken();
    Instant now = clock.instant();
    sessions.save(
        new UserSession(
            UUID.randomUUID(), tenant, userId, tokens.hash(token), now.plus(Duration.ofDays(1)), now));
    return token;
  }

  /** Reads in a transaction of the application's role, bound to the university, or to none. */
  private <T> T asTheApplication(String tenant, java.util.function.Supplier<T> query) {
    return FixtureOwnerTestDataSource.asApplication(
        () ->
            tenant == null
                ? transactions.execute(status -> query.get())
                : TenantContext.callAs(tenant, () -> transactions.execute(status -> query.get())));
  }

  @Test
  @DisplayName("A query is scoped to the university of the user")
  void aQueryIsScopedToTheUniversityOfTheUser() throws Exception {
    UUID coordinator = aPerson(first, UserRole.COORDINATOR);
    UUID own = anEntryInTheLogOf(first);
    UUID others = anEntryInTheLogOf(second);

    String body =
        mockMvc
            .perform(
                get("/api/v1/coordinator/audit-log")
                    .header("Authorization", "Bearer " + sessionTokenOf(first, coordinator)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> ids = JsonPath.read(body, "$.items[*].id");
    assertThat(ids).contains(own.toString()).doesNotContain(others.toString());
  }

  @Test
  @DisplayName("A query without an explicit filter is restricted by the policy")
  void aQueryWithoutAnExplicitFilterIsRestrictedByThePolicy() {
    UUID ofTheFirst = aPerson(first, UserRole.STUDENT);
    UUID ofTheSecond = aPerson(second, UserRole.STUDENT);

    List<User> everybody = asTheApplication(first, () -> users.findAll());
    List<String> universities =
        asTheApplication(
            first, () -> jdbc.queryForList("select tenant_id from identity.users", String.class));

    assertThat(everybody).extracting(User::getId).contains(ofTheFirst).doesNotContain(ofTheSecond);
    assertThat(everybody).extracting(User::getTenantId).containsOnly(first);
    assertThat(universities).containsOnly(first);
  }

  @Test
  @DisplayName("A request without a university gets nothing")
  void aRequestWithoutAUniversityGetsNothing() {
    aPerson(first, UserRole.STUDENT);
    aPerson(second, UserRole.STUDENT);
    assertThat(jdbc.queryForObject("select count(*) from identity.users", Long.class)).isPositive();

    assertThat(
            asTheApplication(
                null, () -> jdbc.queryForObject("select count(*) from identity.users", Long.class)))
        .isZero();
    assertThat(asTheApplication(null, () -> users.findAll())).isEmpty();

    assertThatThrownBy(
            () ->
                asTheApplication(
                    null,
                    () ->
                        jdbc.update(
                            """
                            insert into audit.activity_log
                              (id, tenant_id, actor_role, action, resource_type, occurred_at)
                            values (?, ?, 'SYSTEM', 'SESSION_COMPLETED', 'SESSION', now())
                            """,
                            UUID.randomUUID(),
                            first)))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  @DisplayName("An automated check with data of two universities")
  void anAutomatedCheckWithDataOfTwoUniversities() {
    for (String tenant : List.of(first, second)) {
      UUID student = aPerson(tenant, UserRole.STUDENT);
      anEntryInTheLogOf(tenant);
      FixtureOwnerTestDataSource.asApplication(
          () -> {
            TenantContext.runAs(
                tenant,
                () ->
                    wallet.grant(
                        student,
                        Credits.of(3),
                        CreditType.ALLOCATED,
                        clock.instant().plus(Duration.ofDays(30)),
                        UUID.randomUUID()));
            return null;
          });
    }
    List<String> tables =
        jdbc.queryForList(RowLevelSecurityPolicyTest.TENANT_TABLES, String.class).stream()
            .filter(table -> !RowLevelSecurityPolicyTest.EXEMPT.contains(table))
            .toList();

    List<String> leaking = new ArrayList<>();
    for (String tenant : List.of(first, second)) {
      String other = tenant.equals(first) ? second : first;
      for (String table : tables) {
        long foreign =
            asTheApplication(
                tenant,
                () ->
                    jdbc.queryForObject(
                        "select count(*) from " + table + " where tenant_id is not null"
                            + " and tenant_id <> ?",
                        Long.class,
                        tenant));
        if (foreign > 0) {
          leaking.add(tenant + " sees " + foreign + " foreign rows of " + table);
        }
      }
      // What the other university wrote is there, for the owner, and invisible to this one.
      for (String table :
          List.of("identity.users", "audit.activity_log", "wallet.credit_lots",
              "wallet.ledger_entries", "wallet.credit_accounts")) {
        assertThat(ownerCount(table, other)).as(table + " of " + other).isPositive();
        assertThat(
                asTheApplication(
                    tenant,
                    () ->
                        jdbc.queryForObject(
                            "select count(*) from " + table + " where tenant_id = ?",
                            Long.class,
                            tenant)))
            .as(table + " of " + tenant + " seen by itself")
            .isPositive();
      }
    }

    assertThat(leaking).isEmpty();
  }

  private long ownerCount(String table, String tenant) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where tenant_id = ?", Long.class, tenant);
  }

  @Test
  @DisplayName("The university does not outlive its transaction")
  void theUniversityDoesNotOutliveItsTransaction() {
    for (int i = 0; i < 20; i++) {
      assertThat(
              asTheApplication(
                  first,
                  () ->
                      jdbc.queryForObject(
                          "select current_setting('app.tenant_id', true)", String.class)))
          .isEqualTo(first);
      assertThat(
              asTheApplication(
                  null,
                  () ->
                      jdbc.queryForObject(
                          "select coalesce(current_setting('app.tenant_id', true), '')",
                          String.class)))
          .isEmpty();
    }
    // Outside any transaction the session itself was never given a university.
    assertThat(
            FixtureOwnerTestDataSource.asApplication(
                () ->
                    jdbc.queryForObject(
                        "select coalesce(current_setting('app.tenant_id', true), '')",
                        String.class)))
        .isEmpty();
  }

  @Test
  @DisplayName("What crosses universities on purpose visits each one as itself")
  void whatCrossesUniversitiesOnPurposeVisitsEachOneAsItself() {
    for (String tenant : List.of(first, second)) {
      UUID student = aPerson(tenant, UserRole.STUDENT);
      FixtureOwnerTestDataSource.asApplication(
          () -> {
            TenantContext.runAs(
                tenant,
                () ->
                    wallet.grant(
                        student,
                        Credits.of(2),
                        CreditType.ALLOCATED,
                        clock.instant().plus(Duration.ofHours(1)),
                        UUID.randomUUID()));
            return null;
          });
    }
    clock.advance(Duration.ofHours(2));

    List<String> withWork =
        FixtureOwnerTestDataSource.asApplication(expireCredits::universitiesWithCreditsToExpire);
    List<UniversityAdminView> listed =
        FixtureOwnerTestDataSource.asApplication(listUniversities::execute);

    assertThat(withWork).contains(first, second);
    Map<String, Long> students = new java.util.HashMap<>();
    listed.forEach(view -> students.put(view.code(), view.studentCount()));
    assertThat(students).containsEntry(first, 1L).containsEntry(second, 1L);
  }

  @Test
  @DisplayName("The application's role cannot bypass the policies")
  void theApplicationsRoleCannotBypassThePolicies() {
    Map<String, Object> role =
        jdbc.queryForMap(
            "select rolsuper, rolbypassrls from pg_roles where rolname = ?",
            ApplicationRoleTestConnection.APPLICATION_ROLE);

    assertThat(role).containsEntry("rolsuper", false).containsEntry("rolbypassrls", false);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from pg_class k join pg_roles r on r.oid = k.relowner"
                    + " where r.rolname = ?",
                Long.class,
                ApplicationRoleTestConnection.APPLICATION_ROLE))
        .isZero();
    assertThat(asTheApplication(first, () -> jdbc.queryForObject("select current_user", String.class)))
        .isEqualTo(ApplicationRoleTestConnection.APPLICATION_ROLE);
  }
}
