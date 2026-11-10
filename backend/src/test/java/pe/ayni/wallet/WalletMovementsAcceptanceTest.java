package pe.ayni.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US24, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US24-wallet-movements.feature},
 * with the same names, signed in with real session tokens. Each test has a university and a
 * student of its own, so that the history of one cannot leak into another.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class WalletMovementsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = WalletTestDatabase.INSTANCE;

  private final UUID booking = UUID.randomUUID();
  private final Instant expiresAt = Instant.now().plus(Duration.ofDays(90));

  @Autowired private MockMvc mockMvc;
  @Autowired private WalletApi wallet;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  private SignedInPeople people;
  private String tenant;
  private Person signedIn;
  private UUID student;

  @BeforeEach
  void aGrantAndACharge() {
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("M");
    signedIn = people.aStudent(tenant);
    student = signedIn.id();
    TenantContext.runAs(
        tenant,
        () -> {
          wallet.grant(student, Credits.of(5), CreditType.SEED, expiresAt, UUID.randomUUID());
          wallet.charge(student, Credits.of(2), booking);
        });
  }

  @Test
  @DisplayName("Every movement is listed, newest first")
  void everyMovementIsListedNewestFirst() throws Exception {
    movements(get("/api/v1/wallet/movements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.items[0].reason").value("BOOKING_CHARGE"))
        .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.items[0].amount").value(2))
        .andExpect(jsonPath("$.items[0].creditType").value("SEED"))
        .andExpect(jsonPath("$.items[0].referenceType").value("BOOKING"))
        .andExpect(jsonPath("$.items[0].referenceId").value(booking.toString()))
        .andExpect(jsonPath("$.items[1].reason").value("GRANT"))
        .andExpect(jsonPath("$.items[1].direction").value("CREDIT"))
        .andExpect(jsonPath("$.items[1].amount").value(5));
  }

  @Test
  @DisplayName("The history can be narrowed to one kind of movement")
  void theHistoryCanBeNarrowedToOneKind() throws Exception {
    movements(get("/api/v1/wallet/movements").param("reason", "GRANT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].reason").value("GRANT"));
  }

  @Test
  @DisplayName("The history can be narrowed to a period")
  void theHistoryCanBeNarrowedToAPeriod() throws Exception {
    Instant now = Instant.now();

    movements(
            get("/api/v1/wallet/movements")
                .param("from", now.minus(Duration.ofDays(2)).toString())
                .param("to", now.minus(Duration.ofDays(1)).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    movements(
            get("/api/v1/wallet/movements")
                .param("from", now.minus(Duration.ofHours(1)).toString())
                .param("to", now.plus(Duration.ofHours(1)).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("A long history comes in pages")
  void aLongHistoryComesInPages() throws Exception {
    TenantContext.runAs(
        tenant,
        () -> {
          for (int i = 0; i < 3; i++) {
            wallet.grant(student, Credits.of(1), CreditType.SEED, expiresAt, UUID.randomUUID());
          }
        });

    movements(get("/api/v1/wallet/movements").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3));

    String lastPage =
        movements(get("/api/v1/wallet/movements").param("size", "2").param("page", "2"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Integer> amounts = JsonPath.read(lastPage, "$.items[*].amount");
    List<String> reasons = JsonPath.read(lastPage, "$.items[*].reason");
    assertThat(amounts).containsExactly(5);
    assertThat(reasons).containsExactly("GRANT");
  }

  @Test
  @DisplayName("Only the student's own movements are listed")
  void onlyTheStudentsOwnMovementsAreListed() throws Exception {
    UUID classmate = people.aStudent(tenant).id();
    String otherUniversity = people.aUniversity("N");
    TenantContext.runAs(
        tenant,
        () -> wallet.grant(classmate, Credits.of(7), CreditType.SEED, expiresAt, UUID.randomUUID()));
    // The same person, as far as the identifier goes, in another university.
    TenantContext.runAs(
        otherUniversity,
        () -> wallet.grant(student, Credits.of(9), CreditType.SEED, expiresAt, UUID.randomUUID()));

    String body =
        movements(get("/api/v1/wallet/movements"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Integer> amounts = JsonPath.read(body, "$.items[*].amount");
    assertThat(amounts).containsExactly(2, 5);
  }

  @Test
  @DisplayName("A page outside the limits is refused")
  void aPageOutsideTheLimitsIsRefused() throws Exception {
    movements(get("/api/v1/wallet/movements").param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/api/v1/wallet/movements"));
  }

  private ResultActions movements(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request.header("Authorization", "Bearer " + signedIn.token()));
  }
}
