package pe.ayni.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.AyniApplication;
import pe.ayni.identity.CreditPolicyView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.notifications.EmailParts;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.events.PurchaseConfirmed;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionStarted;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;

/**
 * TS04: every event between modules is delivered, even when a listener fails or the process stops.
 *
 * <p>The events are the real ones, published inside a transaction as their modules publish them,
 * and every listener of every module hears them. A listener is made to fail the way it would in
 * production, by the database refusing its write: a test trigger raises an error on the table the
 * listener writes, and is dropped to let it write again. The retry is the job's own method, called
 * by the test after it moves the clock, which is also the clock the registry dates publications
 * with. Restarting is a second application started on the same database.
 *
 * <p>Identity is simulated: it is what the listeners of notifications and wallet ask for the
 * address of a person and the baseline policy of the university. Emails go over SMTP to GreenMail.
 */
@SpringBootTest(
    properties = {
      "ayni.test.identity-headers=false",
      "spring.mail.host=localhost",
      "spring.mail.port=3025"
    })
@Import(TenancyTestConfig.class)
class ReliableEventsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = EventsTestDatabase.INSTANCE;

  @RegisterExtension
  static final GreenMailExtension INBOX = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final String UPC = "UPC";
  private static final String EMAIL = "ana.torres@upc.edu.pe";
  private static final String STUDENT_CODE = "U202400001";
  private static final String TOKEN = "ts04-secret-token-of-the-link";
  private static final String LINK = "http://localhost:5173/access/confirm?token=" + TOKEN;
  private static final String CODE = "482913";
  private static final Pattern AN_EMAIL_ADDRESS =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  private static final String WALLET_ON_SESSION_COMPLETED =
      "pe.ayni.wallet.application.WalletEventListeners.on(pe.ayni.shared.events.SessionCompleted)";
  private static final String WALLET_ON_STUDENT_ACTIVATED =
      "pe.ayni.wallet.application.WalletEventListeners.on(pe.ayni.shared.events.StudentActivated)";
  private static final String NOTIFICATIONS_ON_ACCESS_REQUESTED =
      "pe.ayni.notifications.application.NotificationEventListeners.on(pe.ayni.shared.events.AccessRequested)";
  private static final String NOTIFICATIONS_ON_PRESENCE_CODE_ISSUED =
      "pe.ayni.notifications.application.NotificationEventListeners.on(pe.ayni.shared.events.PresenceCodeIssued)";

  private final UUID tutor = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();

  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;
  @Autowired private WalletApi wallet;
  @Autowired private TenancyMutableClock clock;
  @Autowired private EventPublicationResubmission resubmission;

  @MockitoBean private IdentityApi identity;

  @BeforeEach
  void aStudentAndTheirUniversity() {
    when(identity.requireUser(any()))
        .thenAnswer(
            call ->
                new UserView(
                    call.getArgument(0),
                    UPC,
                    UserRole.STUDENT,
                    EMAIL,
                    STUDENT_CODE,
                    "Ana Torres",
                    "Software Engineering",
                    "2026-2",
                    null));
    jdbc.execute(
        """
        create or replace function public.ts04_refuse_write() returns trigger
        language plpgsql as $$
        begin
          raise exception 'TS04: the database refuses this write';
        end;
        $$
        """);
  }

  @AfterEach
  void nothingLeftForTheNextTest() {
    allow("wallet.ledger_entries");
    allow("notifications.notifications");
    jdbc.execute("drop trigger if exists ts04_keep on public.event_publication");
    jdbc.update("delete from public.event_publication");
  }

  @Test
  @DisplayName(
      "A publication is stored with the change that produced it, and removed once its listener completes")
  void aPublicationIsStoredWithTheChangeAndRemovedOnceCompleted() {
    SessionCompleted completed = aCompletedSession(2);
    AtomicReference<String> publisher = new AtomicReference<>();

    transactions.executeWithoutResult(
        status -> {
          publisher.set(Thread.currentThread().getName());
          events.publishEvent(completed);
          entityManager.flush();
          // One per listener of the event, in this very transaction: the wallet's among them.
          assertThat(publicationsAbout(completed.sessionId())).isGreaterThan(1);
          assertThat(incompletePublicationsTo(WALLET_ON_SESSION_COMPLETED, completed.sessionId()))
              .isEqualTo(1);
        });

    // The listeners ran when the commit returned, on this thread: the tutor is already credited.
    assertThat(Thread.currentThread().getName()).isEqualTo(publisher.get());
    assertThat(balanceOf(tutor)).isEqualTo(Credits.of(2));
    assertThat(publicationsAbout(completed.sessionId())).isZero();
  }

  @Test
  @DisplayName("A change that rolls back stores no publication")
  void aChangeThatRollsBackStoresNoPublication() {
    SessionCompleted completed = aCompletedSession(2);

    transactions.executeWithoutResult(
        status -> {
          events.publishEvent(completed);
          status.setRollbackOnly();
        });

    assertThat(publicationsAbout(completed.sessionId())).isZero();
    assertThat(balanceOf(tutor)).isEqualTo(Credits.ZERO);
  }

  @Test
  @DisplayName(
      "A listener that fails leaves its publication incomplete, and the retry applies it exactly once")
  void aFailedListenerIsRetriedAndAppliedOnce() {
    SessionCompleted completed = aCompletedSession(2);

    refuse("wallet.ledger_entries");
    publish(completed);

    assertThat(incompletePublicationsTo(WALLET_ON_SESSION_COMPLETED, completed.sessionId()))
        .isEqualTo(1);
    assertThat(balanceOf(tutor)).isEqualTo(Credits.ZERO);
    // Only the wallet failed: every other listener of the event completed.
    assertThat(publicationsAbout(completed.sessionId())).isEqualTo(1);

    allow("wallet.ledger_entries");
    clock.advance(Duration.ofMinutes(2));
    resubmission.resubmitIncompletePublications();

    assertThat(balanceOf(tutor)).isEqualTo(Credits.of(2));
    assertThat(earnedTotalOf(tutor)).isEqualTo(Credits.of(2));
    assertThat(publicationsAbout(completed.sessionId())).isZero();

    // Running it again, and the event arriving again, credit nothing more.
    clock.advance(Duration.ofMinutes(2));
    resubmission.resubmitIncompletePublications();
    publish(completed);

    assertThat(balanceOf(tutor)).isEqualTo(Credits.of(2));
    assertThat(ledgerEntriesOf(completed.sessionId())).isEqualTo(1);
  }

  @Test
  @DisplayName("The retry leaves alone a publication younger than a minute")
  void theRetryLeavesAloneAYoungPublication() {
    SessionCompleted completed = aCompletedSession(2);

    refuse("wallet.ledger_entries");
    publish(completed);
    allow("wallet.ledger_entries");

    clock.advance(Duration.ofSeconds(30));
    resubmission.resubmitIncompletePublications();

    // Perhaps still running somewhere: not taken as failed yet.
    assertThat(incompletePublicationsTo(WALLET_ON_SESSION_COMPLETED, completed.sessionId()))
        .isEqualTo(1);
    assertThat(balanceOf(tutor)).isEqualTo(Credits.ZERO);
  }

  @Test
  @DisplayName(
      "A publication left incomplete when the process stops is delivered when it starts again")
  void anIncompletePublicationIsDeliveredOnRestart() {
    SessionCompleted completed = aCompletedSession(3);

    refuse("wallet.ledger_entries");
    publish(completed);
    allow("wallet.ledger_entries");

    // What a process that stopped before the wallet credited the tutor leaves in the database.
    assertThat(incompletePublicationsTo(WALLET_ON_SESSION_COMPLETED, completed.sessionId()))
        .isEqualTo(1);
    assertThat(balanceOf(tutor)).isEqualTo(Credits.ZERO);

    try (ConfigurableApplicationContext restarted = startTheApplicationAgain()) {
      // Delivered while it started, before anybody asked for anything.
      assertThat(incompletePublicationsTo(WALLET_ON_SESSION_COMPLETED, completed.sessionId()))
          .isZero();
      assertThat(balanceOf(tutor)).isEqualTo(Credits.of(3));
    }
    assertThat(ledgerEntriesOf(completed.sessionId())).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "No stored publication holds an access link, a presence code or an email address")
  void noStoredPublicationHoldsASecret() throws Exception {
    keepEveryStoredPublication();
    UUID session = UUID.randomUUID();

    publish(new AccessRequested(UPC, EMAIL, "LOGIN", LINK, now().plusSeconds(600), now()));
    publish(
        new PresenceCodeIssued(UPC, session, student, CODE, now().plusSeconds(300), now()));
    publish(new StudentActivated(UPC, student, EMAIL, STUDENT_CODE, now()));

    // Delivered in full when it mattered: the link and the code reached the inbox.
    MimeMessage[] received = INBOX.getReceivedMessages();
    assertThat(received).hasSize(2);
    assertThat(bodyOf(received[0])).contains(TOKEN);
    assertThat(bodyOf(received[1])).contains(CODE);

    List<String> stored =
        jdbc.queryForList(
            "select serialized_event from public.ts04_stored_publications", String.class);
    assertThat(
            jdbc.queryForList(
                "select distinct event_type from public.ts04_stored_publications", String.class))
        .contains(
            AccessRequested.class.getName(),
            PresenceCodeIssued.class.getName(),
            StudentActivated.class.getName());
    assertThat(stored)
        .isNotEmpty()
        .noneMatch(text -> text.contains(TOKEN))
        .noneMatch(text -> text.contains(CODE))
        .noneMatch(text -> text.contains(STUDENT_CODE))
        .noneMatch(text -> AN_EMAIL_ADDRESS.matcher(text).find());
  }

  @Test
  @DisplayName("A resumed access link or presence code is dropped, not sent")
  void aResumedLinkOrCodeIsDropped() {
    UUID session = UUID.randomUUID();
    int noticesBefore = noticesOfLinksAndCodes();

    refuse("notifications.notifications");
    publish(new AccessRequested(UPC, EMAIL, "LOGIN", LINK, now().plusSeconds(600), now()));
    publish(
        new PresenceCodeIssued(UPC, session, student, CODE, now().plusSeconds(300), now()));
    allow("notifications.notifications");

    assertThat(incompletePublicationsTo(NOTIFICATIONS_ON_ACCESS_REQUESTED)).isEqualTo(1);
    assertThat(incompletePublicationsTo(NOTIFICATIONS_ON_PRESENCE_CODE_ISSUED)).isEqualTo(1);

    clock.advance(Duration.ofMinutes(2));
    resubmission.resubmitIncompletePublications();

    // Nothing was kept to send: no email, no notice, and nothing left to retry.
    assertThat(INBOX.getReceivedMessages()).isEmpty();
    assertThat(noticesOfLinksAndCodes()).isEqualTo(noticesBefore);
    assertThat(incompletePublicationsTo(NOTIFICATIONS_ON_ACCESS_REQUESTED)).isZero();
    assertThat(incompletePublicationsTo(NOTIFICATIONS_ON_PRESENCE_CODE_ISSUED)).isZero();
  }

  @Test
  @DisplayName("A resumed student activation grants the initial credits once")
  void aResumedActivationGrantsOnce() {
    when(identity.currentPolicy(UPC, PolicyKind.BASELINE))
        .thenReturn(
            Optional.of(
                new CreditPolicyView(UUID.randomUUID(), PolicyKind.BASELINE, Credits.of(5), 30)));
    StudentActivated activated = new StudentActivated(UPC, student, EMAIL, STUDENT_CODE, now());

    refuse("wallet.ledger_entries");
    publish(activated);
    allow("wallet.ledger_entries");

    assertThat(incompletePublicationsTo(WALLET_ON_STUDENT_ACTIVATED, student)).isEqualTo(1);
    assertThat(balanceOf(student)).isEqualTo(Credits.ZERO);

    clock.advance(Duration.ofMinutes(2));
    resubmission.resubmitIncompletePublications();

    assertThat(balanceOf(student)).isEqualTo(Credits.of(5));
    assertThat(incompletePublicationsTo(WALLET_ON_STUDENT_ACTIVATED, student)).isZero();

    // Delivered once more, from memory: still once.
    publish(activated);
    assertThat(balanceOf(student)).isEqualTo(Credits.of(5));
  }

  @Test
  @DisplayName("An audit entry is written once when its event arrives twice")
  void anAuditEntryIsWrittenOnce() {
    SessionStarted started = new SessionStarted(UPC, UUID.randomUUID(), UUID.randomUUID(), now());

    publish(started);
    publish(started);

    assertThat(
            jdbc.queryForObject(
                """
                select count(*) from audit.activity_log
                where tenant_id = ? and action = 'SESSION_STARTED' and resource_id = ?
                """,
                Integer.class,
                UPC,
                started.sessionId()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("A no-show notice is written and sent once when its event arrives twice")
  void aNoShowNoticeIsSentOnce() {
    SessionAbandoned abandoned =
        new SessionAbandoned(
            UPC,
            UUID.randomUUID(),
            UUID.randomUUID(),
            tutor,
            student,
            UUID.randomUUID(),
            true,
            now());

    publish(abandoned);
    publish(abandoned);

    assertThat(
            jdbc.queryForObject(
                """
                select count(*) from notifications.notifications
                where kind = 'TUTOR_NO_SHOW' and payload ->> 'sessionId' = ?
                """,
                Integer.class,
                abandoned.sessionId().toString()))
        .isEqualTo(1);
    assertThat(INBOX.getReceivedMessages()).hasSize(1);
  }

  @Test
  @DisplayName("A purchase confirmation is written and sent once when its event arrives twice")
  void aPurchaseConfirmationIsSentOnce() {
    PurchaseConfirmed confirmed =
        new PurchaseConfirmed(UPC, UUID.randomUUID(), student, Credits.of(3), now());

    publish(confirmed);
    publish(confirmed);

    assertThat(
            jdbc.queryForObject(
                """
                select count(*) from notifications.notifications
                where kind = 'PURCHASE_CONFIRMED' and payload ->> 'purchaseId' = ?
                """,
                Integer.class,
                confirmed.purchaseId().toString()))
        .isEqualTo(1);
    assertThat(INBOX.getReceivedMessages()).hasSize(1);
    assertThat(balanceOf(student)).isEqualTo(Credits.of(3));
  }

  // ---------------------------------------------------------------------------------------------

  private SessionCompleted aCompletedSession(int credits) {
    return new SessionCompleted(
        UPC,
        UUID.randomUUID(),
        UUID.randomUUID(),
        tutor,
        student,
        UUID.randomUUID(),
        Credits.of(credits),
        now());
  }

  private Instant now() {
    return clock.instant();
  }

  private void publish(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  /** The database refuses every new row of the table, as it would if it could not write. */
  private void refuse(String table) {
    jdbc.execute(
        "create trigger ts04_refuse before insert on %s for each row execute function public.ts04_refuse_write()"
            .formatted(table));
  }

  private void allow(String table) {
    jdbc.execute("drop trigger if exists ts04_refuse on %s".formatted(table));
  }

  /** Copies every publication the registry stores, so it can be read after it was removed. */
  private void keepEveryStoredPublication() {
    jdbc.execute(
        "create table if not exists public.ts04_stored_publications (event_type text, serialized_event text)");
    jdbc.update("delete from public.ts04_stored_publications");
    jdbc.execute(
        """
        create or replace function public.ts04_keep_publication() returns trigger
        language plpgsql security definer as $$
        begin
          insert into public.ts04_stored_publications values (new.event_type, new.serialized_event);
          return new;
        end;
        $$
        """);
    jdbc.execute(
        "create trigger ts04_keep after insert on public.event_publication for each row execute function public.ts04_keep_publication()");
  }

  private int publicationsAbout(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from public.event_publication where serialized_event like ?",
        Integer.class,
        "%" + id + "%");
  }

  private int incompletePublicationsTo(String listener, UUID about) {
    return jdbc.queryForObject(
        """
        select count(*) from public.event_publication
        where completion_date is null and listener_id = ? and serialized_event like ?
        """,
        Integer.class,
        listener,
        "%" + about + "%");
  }

  private int incompletePublicationsTo(String listener) {
    return jdbc.queryForObject(
        "select count(*) from public.event_publication where completion_date is null and listener_id = ?",
        Integer.class,
        listener);
  }

  private int ledgerEntriesOf(UUID session) {
    return jdbc.queryForObject(
        "select count(*) from wallet.ledger_entries where tenant_id = ? and reference_id = ?",
        Integer.class,
        UPC,
        session);
  }

  private int noticesOfLinksAndCodes() {
    return jdbc.queryForObject(
        "select count(*) from notifications.notifications where kind in ('ACCESS_LINK', 'PRESENCE_CODE')",
        Integer.class);
  }

  private Credits balanceOf(UUID userId) {
    return TenantContext.callAs(UPC, () -> wallet.balanceOf(userId).available());
  }

  private Credits earnedTotalOf(UUID userId) {
    return TenantContext.callAs(UPC, () -> wallet.earnedTotal(userId));
  }

  private static String bodyOf(MimeMessage message) throws Exception {
    return EmailParts.plainTextOf(message);
  }

  /**
   * The application again, on the same database: a process that starts after the last one stopped.
   * Only the classes of the application, none of the tests', and connected as the application's
   * role, as under docker compose.
   */
  private static ConfigurableApplicationContext startTheApplicationAgain() {
    return new SpringApplicationBuilder(AyniApplication.class)
        .initializers(
            context ->
                context.getBeanFactory().registerSingleton("ts04OnlyTheApplication", new OnlyTheApplication()))
        // As arguments of the command line, so that they win over application.yml.
        .run(
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + ApplicationRoleTestConnection.APPLICATION_ROLE,
            "--spring.datasource.password=" + ApplicationRoleTestConnection.APPLICATION_ROLE,
            "--spring.flyway.url=" + POSTGRES.getJdbcUrl(),
            "--server.port=0",
            "--spring.modulith.events.republish-outstanding-events-on-restart=true");
  }

  /** Leaves out of the scan every class compiled from {@code src/test}. */
  static final class OnlyTheApplication extends TypeExcludeFilter {

    @Override
    public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
      try {
        return reader.getResource().getURL().toString().contains("/test-classes/");
      } catch (IOException unreadable) {
        throw new UncheckedIOException(unreadable);
      }
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof OnlyTheApplication;
    }

    @Override
    public int hashCode() {
      return OnlyTheApplication.class.hashCode();
    }
  }
}
