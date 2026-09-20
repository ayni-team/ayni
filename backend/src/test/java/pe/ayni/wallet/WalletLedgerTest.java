package pe.ayni.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.application.ExpireCredits;
import pe.ayni.wallet.domain.model.LedgerEntry;
import pe.ayni.wallet.domain.services.LedgerChain;
import pe.ayni.wallet.infrastructure.LedgerEntryRepository;

/**
 * The promises the ledger makes, checked against the database that has to keep them.
 *
 * <p>None of these can be tested anywhere else: the trigger belongs to PostgreSQL, the agreement
 * between the balance and the history is about rows that were really written, the chain of hashes
 * only proves anything after the entries have been through the database and come back, and
 * appending from two requests at once needs two real connections to be anything more than a hope.
 */
@SpringBootTest
class WalletLedgerTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = WalletTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  private final UUID student = UUID.randomUUID();

  @Autowired private WalletApi wallet;
  @Autowired private ExpireCredits expireCredits;
  @Autowired private LedgerEntryRepository entries;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("the database refuses to change or remove a ledger entry")
  void theLedgerIsAppendOnly() {

    grant(4, CreditType.EARNED, null);
    UUID anEntry = lastEntryOfThisStudent();

    // Not "the application does not do this": the application cannot do this, and neither can
    // anyone with a psql prompt. A correction is a new ADJUSTMENT entry.
    assertThatThrownBy(
            () -> jdbc.update("update wallet.ledger_entries set amount = 999 where id = ?", anEntry))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append only");

    assertThatThrownBy(
            () -> jdbc.update("delete from wallet.ledger_entries where id = ?", anEntry))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append only");

    assertThat(jdbc.queryForObject("select amount from wallet.ledger_entries where id = ?",
            Integer.class, anEntry))
        .isEqualTo(4);
  }

  @Test
  @DisplayName("the balance equals the movements added up")
  void theBalanceAgreesWithTheHistory() {

    UUID booking = UUID.randomUUID();

    grant(10, CreditType.SEED, Instant.now().plus(Duration.ofDays(30)));
    grant(4, CreditType.EARNED, null);
    grant(5, CreditType.SEED, Instant.now().minus(Duration.ofDays(1)));

    TenantContext.runAs(
        UPC,
        () -> {
          wallet.charge(student, Credits.of(3), booking);
          wallet.refund(booking);
          // Once this has run, the credits that died have left the history too. Before it, the
          // balance is already right and the history is not: that gap is why the job exists.
          expireCredits.forCurrentUniversity();
        });

    Credits available = balanceOf();
    UUID account = accountId();

    int credited = sumOf(account, "CREDIT");
    int debited = sumOf(account, "DEBIT");

    assertThat(available.amount()).isEqualTo(credited - debited);
    // 10 granted, 5 that expired, 4 earned: everything except the expired grant.
    assertThat(available).isEqualTo(Credits.of(14));
  }

  @Test
  @DisplayName("the chain of hashes still holds after a round trip through the database")
  void theChainHoldsAfterARoundTrip() {

    grant(6, CreditType.SEED, Instant.now().plus(Duration.ofDays(15)));
    grant(2, CreditType.PURCHASED, null);

    // Read back from PostgreSQL, not from memory: this is what catches an instant that lost
    // precision on the way in and no longer hashes to what was stored.
    List<LedgerEntry> ledger = entries.findByTenantIdOrderBySequenceNumberAsc(UPC);

    assertThat(ledger).isNotEmpty();
    assertThat(LedgerChain.firstTamperedEntry(ledger)).isEmpty();
    assertThat(ledger.getFirst().previousHash()).isNull();
  }

  @Test
  @DisplayName("appending from several requests at once numbers the entries without gaps")
  void simultaneousAppendsQueueBehindOneAnother() throws Exception {

    // A university of its own, so the numbering starts at one and the assertion can be exact
    // instead of relative to whatever the other tests in this class already wrote.
    String tenant = "CONCURRENCY";
    int writers = 8;

    ExecutorService pool = Executors.newFixedThreadPool(writers);
    CountDownLatch startTogether = new CountDownLatch(1);
    List<Future<?>> appends = new ArrayList<>();

    try {
      for (int i = 0; i < writers; i++) {
        UUID tutor = UUID.randomUUID();
        appends.add(
            pool.submit(
                () -> {
                  startTogether.await();
                  TenantContext.runAs(
                      tenant,
                      () ->
                          wallet.grant(
                              tutor, Credits.of(1), CreditType.EARNED, null, UUID.randomUUID()));
                  return null;
                }));
      }
      startTogether.countDown();

      // Every one of them has to get through. Locking the last row instead of the university let
      // the second writer wake up still believing the old tail was the tail, and it was rejected
      // by uq_ledger_entries_sequence after having done all of its work: inside a booking, that
      // takes the whole reservation down.
      for (Future<?> append : appends) {
        append.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    List<LedgerEntry> ledger = entries.findByTenantIdOrderBySequenceNumberAsc(tenant);

    assertThat(ledger)
        .extracting(LedgerEntry::sequenceNumber)
        .containsExactlyElementsOf(LongStream.rangeClosed(1, writers).boxed().toList());
    // The other half of what the lock buys: they chained in that same order.
    assertThat(LedgerChain.firstTamperedEntry(ledger)).isEmpty();
  }

  private void grant(int amount, CreditType type, Instant expiresAt) {
    TenantContext.runAs(
        UPC, () -> wallet.grant(student, Credits.of(amount), type, expiresAt, UUID.randomUUID()));
  }

  private Credits balanceOf() {
    Credits[] available = new Credits[1];
    TenantContext.runAs(UPC, () -> available[0] = wallet.balanceOf(student).available());
    return available[0];
  }

  private UUID accountId() {
    return jdbc.queryForObject(
        "select id from wallet.credit_accounts where tenant_id = ? and user_id = ?",
        UUID.class,
        UPC,
        student);
  }

  private int sumOf(UUID account, String direction) {
    Integer total =
        jdbc.queryForObject(
            """
            select coalesce(sum(amount), 0) from wallet.ledger_entries
            where tenant_id = ? and account_id = ? and direction = ?
            """,
            Integer.class,
            UPC,
            account,
            direction);
    return total == null ? 0 : total;
  }

  private UUID lastEntryOfThisStudent() {
    return jdbc.queryForObject(
        """
        select id from wallet.ledger_entries
        where tenant_id = ? and account_id = ? order by sequence_number desc limit 1
        """,
        UUID.class,
        UPC,
        accountId());
  }
}
