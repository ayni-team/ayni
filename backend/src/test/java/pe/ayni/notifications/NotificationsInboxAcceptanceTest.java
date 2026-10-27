package pe.ayni.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.notifications.infrastructure.NotificationRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The inbox over HTTP against a real PostgreSQL, with real session tokens (TS03): one test per
 * scenario of {@code features/notifications-inbox.feature}, with the same display name.
 *
 * <p>The notices are written the way the delivery flows write them, as {@link Notification}
 * entities saved for a university, with the creation date a scenario needs. The clock is the one of
 * the application, moved by the scenarios that depend on when a notice was read.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@Import(NotificationsTestConfig.class)
class NotificationsInboxAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = NotificationsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;
  @Autowired private NotificationRepository notifications;
  @Autowired private TransactionTemplate transactions;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private PlatformAdminRepository admins;
  @Autowired private AccessTokenGenerator tokens;

  private SignedInPeople people;
  private String university;
  private Person ana;
  private Person bruno;
  private Person carlos;

  @BeforeEach
  void aUniversityAndItsPeopleSignedIn() {
    clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    university = people.aUniversity("N");
    ana = people.aStudent(university);
    bruno = people.aStudent(university);
    carlos = people.aCoordinator(university);
  }

  @Test
  @DisplayName("A student sees only their own notices")
  void aStudentSeesOnlyTheirOwnNotices() throws Exception {
    // Given Ana has two notices and Bruno has one
    UUID first = aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(30));
    UUID second = aNotice(ana, NotificationKind.PURCHASE_CONFIRMED, minutesAgo(10));
    aNotice(bruno, NotificationKind.TUTOR_NO_SHOW, minutesAgo(5));

    // When Ana lists her notices
    String answer =
        list(ana, "")
            .andExpect(status().isOk())
            // Then she sees her two notices with their kind, payload, creation date and reading date
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].id").value(second.toString()))
            .andExpect(jsonPath("$.items[0].kind").value("PURCHASE_CONFIRMED"))
            .andExpect(jsonPath("$.items[0].payload.noticeId").value(second.toString()))
            .andExpect(jsonPath("$.items[0].createdAt").value(minutesAgo(10).toString()))
            .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
            .andExpect(jsonPath("$.items[1].id").value(first.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // And neither the address nor the delivery failure is part of the answer
    assertThat(answer).doesNotContain("recipientEmail", "failedReason", "@");
  }

  @Test
  @DisplayName("A coordinator sees their own notices")
  void aCoordinatorSeesTheirOwnNotices() throws Exception {
    // Given Carlos has one notice and Ana has two
    UUID his = aNotice(carlos, NotificationKind.REQUEST_RESOLVED, minutesAgo(20));
    aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(30));
    aNotice(ana, NotificationKind.PURCHASE_CONFIRMED, minutesAgo(10));

    // When Carlos lists his notices
    list(carlos, "")
        .andExpect(status().isOk())
        // Then he sees his one notice
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(his.toString()))
        .andExpect(jsonPath("$.unreadCount").value(1));
  }

  @Test
  @DisplayName("Another student of the same university does not see them")
  void anotherStudentOfTheSameUniversityDoesNotSeeThem() throws Exception {
    // Given Ana has two notices
    aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(30));
    aNotice(ana, NotificationKind.PURCHASE_CONFIRMED, minutesAgo(10));

    // When Bruno lists his notices
    list(bruno, "")
        .andExpect(status().isOk())
        // Then he sees none and has none unread
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.unreadCount").value(0));
  }

  @Test
  @DisplayName("Another university sees nothing of them")
  void anotherUniversitySeesNothingOfThem() throws Exception {
    // Given Ana has two notices
    aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(30));
    aNotice(ana, NotificationKind.PURCHASE_CONFIRMED, minutesAgo(10));
    // And Dora is a student of another university
    Person dora = people.aStudent(people.aUniversity("N"));

    // When Dora lists her notices
    list(dora, "")
        .andExpect(status().isOk())
        // Then she sees none and has none unread
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.unreadCount").value(0));
  }

  @Test
  @DisplayName("Emailed links and codes are not listed")
  void emailedLinksAndCodesAreNotListed() throws Exception {
    // Given Ana has an access link, a presence code and one notice of the application
    aNotice(ana, NotificationKind.ACCESS_LINK, minutesAgo(30));
    aNotice(ana, NotificationKind.PRESENCE_CODE, minutesAgo(20));
    UUID notice = aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(10));

    // When Ana lists her notices
    list(ana, "")
        .andExpect(status().isOk())
        // Then she sees only the notice of the application
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(notice.toString()))
        .andExpect(jsonPath("$.total").value(1))
        // And the unread count does not include the link or the code
        .andExpect(jsonPath("$.unreadCount").value(1));
  }

  @Test
  @DisplayName("A notice is listed even when its email failed")
  void aNoticeIsListedEvenWhenItsEmailFailed() throws Exception {
    // Given Ana has a notice whose email could not be delivered
    UUID notice = aFailedNotice(ana, "Mailbox unavailable");

    // When Ana lists her notices
    String answer =
        list(ana, "")
            .andExpect(status().isOk())
            // Then she sees it, with no reason for the failure
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(notice.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(answer).doesNotContain("Mailbox unavailable", "failedReason");
  }

  @Test
  @DisplayName("Notices come newest first, a page at a time")
  void noticesComeNewestFirstAPageAtATime() throws Exception {
    // Given Ana has twenty-five notices
    for (int minutes = 25; minutes >= 1; minutes--) {
      aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(minutes));
    }

    // When she lists her notices without asking for a size
    String first =
        list(ana, "")
            .andExpect(status().isOk())
            // Then she gets the newest twenty, the total is twenty-five and the size is twenty
            .andExpect(jsonPath("$.items.length()").value(20))
            .andExpect(jsonPath("$.total").value(25))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.unreadCount").value(25))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // And the second page has the other five, older than those of the first
    String second =
        list(ana, "?page=1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(5))
            .andExpect(jsonPath("$.page").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> newestFirst = JsonPath.read(first, "$.items[*].createdAt");
    assertThat(newestFirst).isSortedAccordingTo((a, b) -> b.compareTo(a));
    assertThat(newestFirst.get(0)).isEqualTo(minutesAgo(1).toString());
    List<String> older = JsonPath.read(second, "$.items[*].createdAt");
    assertThat(older.get(0).compareTo(newestFirst.get(19))).isLessThan(0);
    assertThat(older.get(4)).isEqualTo(minutesAgo(25).toString());
  }

  @Test
  @DisplayName("A page or a size outside the limits is refused")
  void aPageOrASizeOutsideTheLimitsIsRefused() throws Exception {
    // When Ana asks for a size of 101, a size of 0 or a page below 0
    for (String query : List.of("?size=101", "?size=0", "?page=-1", "?size=many")) {
      // Then each answer is 400 and has the shape of every error
      list(ana, query)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.error").value("Bad Request"))
          .andExpect(jsonPath("$.message").exists())
          .andExpect(jsonPath("$.timestamp").exists())
          .andExpect(jsonPath("$.path").value("/api/v1/notifications"));
    }
    // The most it accepts is a hundred
    list(ana, "?size=100").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
  }

  @Test
  @DisplayName("Only the unread ones on request")
  void onlyTheUnreadOnesOnRequest() throws Exception {
    // Given Ana has three notices and has read one
    UUID read = aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(30));
    UUID newer = aNotice(ana, NotificationKind.PURCHASE_CONFIRMED, minutesAgo(20));
    UUID newest = aNotice(ana, NotificationKind.REQUEST_RESOLVED, minutesAgo(10));
    mark(ana, read).andExpect(status().isNoContent());

    // When she lists her unread notices
    list(ana, "?unread=true")
        .andExpect(status().isOk())
        // Then she sees the two she has not read
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].id").value(newest.toString()))
        .andExpect(jsonPath("$.items[1].id").value(newer.toString()))
        .andExpect(jsonPath("$.total").value(2))
        // And the unread count is two
        .andExpect(jsonPath("$.unreadCount").value(2));
    list(ana, "").andExpect(jsonPath("$.total").value(3));
  }

  @Test
  @DisplayName("Reading a notice lowers the unread count")
  void readingANoticeLowersTheUnreadCount() throws Exception {
    // Given Ana has two unread notices
    UUID first = aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(30));
    UUID second = aNotice(ana, NotificationKind.PURCHASE_CONFIRMED, minutesAgo(10));
    list(ana, "").andExpect(jsonPath("$.unreadCount").value(2));

    // When she marks one as read
    // Then the answer is 204
    mark(ana, first).andExpect(status().isNoContent());

    // And the notice shows when it was read
    // And the unread count is one
    list(ana, "")
        .andExpect(jsonPath("$.unreadCount").value(1))
        .andExpect(jsonPath("$.items[?(@.id=='" + first + "')].readAt").value(clock.instant().toString()))
        .andExpect(jsonPath("$.items[?(@.id=='" + second + "')].readAt").value((Object) null));
  }

  @Test
  @DisplayName("Reading a notice again keeps its first date")
  void readingANoticeAgainKeepsItsFirstDate() throws Exception {
    // Given Ana read a notice a while ago
    UUID notice = aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(30));
    Instant firstTime = clock.instant();
    mark(ana, notice).andExpect(status().isNoContent());
    clock.advance(Duration.ofHours(2));

    // When she marks it as read again
    // Then the answer is 204
    mark(ana, notice).andExpect(status().isNoContent());

    // And the notice keeps the date of the first time
    assertThat(readAt(notice)).isEqualTo(firstTime);
    list(ana, "?unread=true").andExpect(jsonPath("$.total").value(0));
  }

  @Test
  @DisplayName("Two requests marking the same notice take turns and keep the first date")
  void twoRequestsMarkingTheSameNoticeTakeTurns() throws Exception {
    // Given a notice that one request is marking as read and has not finished
    UUID notice = aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(30));
    Instant firstTime = clock.instant();
    CyclicBarrier firstHoldsIt = new CyclicBarrier(2);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<?> first =
          pool.submit(
              () ->
                  TenantContext.runAs(
                      university,
                      () ->
                          transactions.executeWithoutResult(
                              transaction -> {
                                notifications
                                    .lockOwned(
                                        university,
                                        ana.id(),
                                        NotificationKind.listed(),
                                        notice)
                                    .orElseThrow()
                                    .markRead(firstTime);
                                await(firstHoldsIt);
                                pause(Duration.ofMillis(700));
                              })));
      await(firstHoldsIt);

      // When a second request marks it as read, later
      clock.advance(Duration.ofHours(1));
      long started = System.nanoTime();
      mark(ana, notice).andExpect(status().isNoContent());
      long waited = Duration.ofNanos(System.nanoTime() - started).toMillis();
      first.get(10, TimeUnit.SECONDS);

      // Then the second waits for the first
      assertThat(waited).isGreaterThanOrEqualTo(300);
      // And the notice keeps the date of the first
      assertThat(readAt(notice)).isEqualTo(firstTime);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("Reading all leaves none unread")
  void readingAllLeavesNoneUnread() throws Exception {
    // Given Ana has three notices and has read one a while ago, and Bruno has one
    UUID readBefore = aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(30));
    UUID second = aNotice(ana, NotificationKind.PURCHASE_CONFIRMED, minutesAgo(20));
    UUID third = aNotice(ana, NotificationKind.REQUEST_RESOLVED, minutesAgo(10));
    UUID accessLink = aNotice(ana, NotificationKind.ACCESS_LINK, minutesAgo(5));
    UUID brunos = aNotice(bruno, NotificationKind.TUTOR_NO_SHOW, minutesAgo(5));
    Instant earlier = clock.instant();
    mark(ana, readBefore).andExpect(status().isNoContent());
    clock.advance(Duration.ofHours(3));

    // When she marks all as read
    // Then the answer is 204
    markAll(ana).andExpect(status().isNoContent());

    // And none of hers is unread and the one she had read keeps its date
    list(ana, "")
        .andExpect(jsonPath("$.unreadCount").value(0))
        .andExpect(jsonPath("$.items[?(@.readAt == null)]").isEmpty());
    assertThat(readAt(readBefore)).isEqualTo(earlier);
    assertThat(readAt(second)).isEqualTo(clock.instant());
    assertThat(readAt(third)).isEqualTo(clock.instant());
    // The link is not a notice of the application, so it was not touched
    assertThat(readAt(accessLink)).isNull();
    // And Bruno's notice is still unread
    assertThat(readAt(brunos)).isNull();
    list(bruno, "").andExpect(jsonPath("$.unreadCount").value(1));
    // Nothing unread is not an error
    markAll(ana).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Somebody else's notice cannot be marked as read")
  void somebodyElsesNoticeCannotBeMarkedAsRead() throws Exception {
    // Given Ana has a notice, and so do Bruno and a student of another university
    aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(30));
    UUID brunos = aNotice(bruno, NotificationKind.TUTOR_NO_SHOW, minutesAgo(20));
    Person dora = people.aStudent(people.aUniversity("N"));
    UUID doras = aNotice(dora, NotificationKind.TUTOR_NO_SHOW, minutesAgo(10));

    // When Ana marks Bruno's notice, the one of the other university and one that does not exist
    // Then each answer is 404
    for (UUID notice : List.of(brunos, doras, UUID.randomUUID())) {
      mark(ana, notice)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.error").value("Not Found"))
          .andExpect(jsonPath("$.message").exists())
          .andExpect(jsonPath("$.timestamp").exists())
          .andExpect(jsonPath("$.path").value("/api/v1/notifications/" + notice + "/read"));
    }

    // And neither notice was marked
    assertThat(readAt(brunos)).isNull();
    assertThat(readAt(doras)).isNull();
  }

  @Test
  @DisplayName("An emailed link cannot be marked as read")
  void anEmailedLinkCannotBeMarkedAsRead() throws Exception {
    // Given Ana has an access link
    UUID link = aNotice(ana, NotificationKind.ACCESS_LINK, minutesAgo(5));

    // When she marks it as read
    // Then the answer is 404
    mark(ana, link).andExpect(status().isNotFound());
    assertThat(readAt(link)).isNull();
  }

  @Test
  @DisplayName("A request without a session is refused")
  void aRequestWithoutASessionIsRefused() throws Exception {
    UUID notice = aNotice(ana, NotificationKind.TUTOR_NO_SHOW, minutesAgo(5));

    // When somebody lists notices without a session token, or with one nobody issued
    // Then the answer is 401
    mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer nobody"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/v1/notifications/{id}/read", notice))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    assertThat(readAt(notice)).isNull();
  }

  @Test
  @DisplayName("A platform administrator is refused")
  void aPlatformAdministratorIsRefused() throws Exception {
    // Given a signed in platform administrator
    UUID adminId = UUID.randomUUID();
    admins.save(
        new PlatformAdmin(
            adminId, "admin-" + adminId + "@ayni.test", "Admin", PlatformAdminStatus.ACTIVE, clock.instant()));
    String token = tokens.generate().rawToken();
    userSessions.save(
        UserSession.forPlatformAdmin(
            UUID.randomUUID(),
            adminId,
            tokens.hash(token),
            clock.instant().plus(Duration.ofDays(30)),
            clock.instant()));

    // When they list notices or mark all as read
    // Then both answers are 403
    mockMvc
        .perform(bearer(get("/api/v1/notifications"), token))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(bearer(post("/api/v1/notifications/read-all"), token))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------------------------

  private Instant minutesAgo(int minutes) {
    return clock.instant().minus(Duration.ofMinutes(minutes));
  }

  /** A notice saved for the person, as the delivery flows do: pending first. */
  private UUID aNotice(Person recipient, NotificationKind kind, Instant createdAt) {
    UUID id = UUID.randomUUID();
    save(recipient, Notification.pending(id, recipient.tenantId(), recipient.id(),
        recipient.id() + "@" + recipient.tenantId().toLowerCase() + ".edu.pe", kind,
        Map.of("noticeId", id.toString()), createdAt));
    return id;
  }

  private UUID aFailedNotice(Person recipient, String reason) {
    UUID id = UUID.randomUUID();
    Notification notice =
        Notification.pending(
            id,
            recipient.tenantId(),
            recipient.id(),
            recipient.id() + "@" + recipient.tenantId().toLowerCase() + ".edu.pe",
            NotificationKind.TUTOR_NO_SHOW,
            Map.of("sessionId", UUID.randomUUID().toString()),
            minutesAgo(5));
    notice.markFailed(reason);
    save(recipient, notice);
    return id;
  }

  private void save(Person recipient, Notification notice) {
    TenantContext.runAs(recipient.tenantId(), () -> notifications.save(notice));
  }

  private Instant readAt(UUID notice) {
    Timestamp read =
        jdbc.queryForObject(
            "select read_at from notifications.notifications where id = ?", Timestamp.class, notice);
    return read == null ? null : read.toInstant();
  }

  private ResultActions list(Person person, String query) throws Exception {
    return mockMvc.perform(bearer(get("/api/v1/notifications" + query), person.token()));
  }

  private ResultActions mark(Person person, UUID notice) throws Exception {
    return mockMvc.perform(
        bearer(post("/api/v1/notifications/{id}/read", notice), person.token()));
  }

  private ResultActions markAll(Person person) throws Exception {
    return mockMvc.perform(bearer(post("/api/v1/notifications/read-all"), person.token()));
  }

  private static MockHttpServletRequestBuilder bearer(
      MockHttpServletRequestBuilder request, String token) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private static void await(CyclicBarrier barrier) {
    try {
      barrier.await(10, TimeUnit.SECONDS);
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static void pause(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
