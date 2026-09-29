package pe.ayni.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.OpenHourView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.reputation.ReputationApi;
import pe.ayni.reputation.TutorStandingView;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.HoursGenerated;
import pe.ayni.shared.events.HoursWithdrawn;
import pe.ayni.shared.events.SessionRated;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.SkillsApi;

/**
 * Every event matching listens to, and what it leaves in {@code matching.available_offers}.
 *
 * <p>Each event is published inside a transaction, as its module publishes it, and heard once that
 * transaction commits. Each one is also heard twice, because a retried event must neither duplicate
 * an offer nor fail on one that is already there.
 *
 * <p>The four modules matching asks are mocked with the answers the real ones give. The table is
 * read as rows, so what is checked is what the search will read.
 */
@SpringBootTest
@DisplayName("How the search is kept up to date")
class OfferProjectionListenersTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = MatchingTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  /** A tutor, two courses and three hours of their own, so no test sees another one's rows. */
  private final UUID tutor = UUID.randomUUID();

  private final UUID databases = UUID.randomUUID();
  private final UUID calculus = UUID.randomUUID();
  private final Instant tomorrow =
      Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));
  private final List<HoursGenerated.Block> hours =
      List.of(
          new HoursGenerated.Block(UUID.randomUUID(), tomorrow),
          new HoursGenerated.Block(UUID.randomUUID(), tomorrow.plus(Duration.ofHours(1))),
          new HoursGenerated.Block(UUID.randomUUID(), tomorrow.plus(Duration.ofHours(2))));

  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private BookingApi booking;
  @MockitoBean private SkillsApi skills;
  @MockitoBean private IdentityApi identity;
  @MockitoBean private ReputationApi reputation;

  /** The universities reputation was asked from: the listener has to bind the event's. */
  private final List<String> askedFrom = new ArrayList<>();

  @BeforeEach
  void aTutorOfDatabasesWithAReputationAndNothingInCalculus() {
    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of(databases));
    when(identity.requireUser(tutor))
        .thenReturn(
            new UserView(
                tutor, UPC, UserRole.STUDENT, "carla@upc.edu.pe", "U20201", "Carla Ruiz",
                "Software Engineering", "2026-2", null));
    when(reputation.standingOf(eq(tutor), any()))
        .thenAnswer(
            call -> {
              askedFrom.add(TenantContext.get());
              return call.getArgument(1).equals(databases)
                  ? Optional.of(standing(databases, 6, 4, "4.75"))
                  : Optional.empty();
            });
  }

  private TutorStandingView standing(UUID course, int sessions, int ratings, String average) {
    return new TutorStandingView(
        tutor, course, sessions, ratings, average == null ? null : new BigDecimal(average));
  }

  private void publishTwice(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  private HoursGenerated hoursGenerated() {
    return new HoursGenerated(UPC, tutor, hours, Instant.now());
  }

  private UUID block(int index) {
    return hours.get(index).blockId();
  }

  private List<Map<String, Object>> offersOfTheTutor() {
    return jdbc.queryForList(
        """
        select * from matching.available_offers
        where tenant_id = ? and tutor_id = ?
        order by starts_at, catalog_item_id
        """,
        UPC,
        tutor);
  }

  private List<UUID> blocksOffered(UUID course) {
    return jdbc.queryForList(
        """
        select block_id from matching.available_offers
        where tenant_id = ? and tutor_id = ? and catalog_item_id = ?
        order by starts_at
        """,
        UUID.class,
        UPC,
        tutor,
        course);
  }

  private List<OpenHourView> open(int... indexes) {
    List<OpenHourView> open = new ArrayList<>();
    for (int index : indexes) {
      HoursGenerated.Block block = hours.get(index);
      open.add(
          new OpenHourView(
              block.blockId(), tutor, block.startsAt(), block.startsAt().plus(Duration.ofHours(1))));
    }
    return open;
  }

  @Test
  @DisplayName("HoursGenerated offers every hour for every enabled course, with name and standing")
  void generatedHoursAreOffered() {

    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of(databases, calculus));

    publishTwice(hoursGenerated());

    List<Map<String, Object>> rows = offersOfTheTutor();
    assertThat(rows).hasSize(6);
    Map<String, Object> first = rows.stream()
        .filter(row -> row.get("block_id").equals(block(0)) && row.get("catalog_item_id").equals(databases))
        .findFirst()
        .orElseThrow();
    assertThat(first.get("tutor_name")).isEqualTo("Carla Ruiz");
    assertThat(((Timestamp) first.get("starts_at")).toInstant()).isEqualTo(tomorrow);
    assertThat((BigDecimal) first.get("average_stars")).isEqualByComparingTo("4.75");
    assertThat(first.get("ratings_count")).isEqualTo(4);
    assertThat(first.get("sessions_taught")).isEqualTo(6);

    // Never taught calculus: no standing at all, so a new tutor with nothing counted.
    assertThat(rows)
        .filteredOn(row -> row.get("catalog_item_id").equals(calculus))
        .hasSize(3)
        .allSatisfy(
            row -> {
              assertThat(row.get("average_stars")).isNull();
              assertThat(row.get("ratings_count")).isEqualTo(0);
              assertThat(row.get("sessions_taught")).isEqualTo(0);
            });
  }

  @Test
  @DisplayName("below three ratings the offer carries no average, only the counts")
  void belowThreeRatingsThereIsNoAverage() {

    // Reputation already hides the average of a tutor with two ratings.
    when(reputation.standingOf(tutor, databases))
        .thenReturn(Optional.of(standing(databases, 2, 2, null)));

    publishTwice(hoursGenerated());

    assertThat(offersOfTheTutor())
        .hasSize(3)
        .allSatisfy(
            row -> {
              assertThat(row.get("average_stars")).isNull();
              assertThat(row.get("ratings_count")).isEqualTo(2);
            });
  }

  @Test
  @DisplayName("the hours of a tutor with no enabled course are not offered")
  void aTutorWithoutCoursesIsNotOffered() {

    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of());

    publishTwice(hoursGenerated());

    assertThat(offersOfTheTutor()).isEmpty();
  }

  @Test
  @DisplayName("the offer is written in the university of the event, which is bound while it is")
  void theUniversityOfTheEventIsBound() {

    transactions.executeWithoutResult(
        status -> events.publishEvent(new HoursGenerated("PUCP", tutor, hours, Instant.now())));

    assertThat(offersOfTheTutor()).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from matching.available_offers where tenant_id = ? and tutor_id = ?",
                Integer.class,
                "PUCP",
                tutor))
        .isEqualTo(3);
    assertThat(askedFrom).containsOnly("PUCP");
  }

  @Test
  @DisplayName("hours whose generation rolled back are never offered")
  void aRolledBackGenerationOffersNothing() {

    transactions.executeWithoutResult(
        status -> {
          events.publishEvent(hoursGenerated());
          status.setRollbackOnly();
        });

    assertThat(offersOfTheTutor()).isEmpty();
  }

  @Test
  @DisplayName("SkillEnabled offers the new course in the hours booking still has open")
  void anEnabledCourseIsOfferedInTheOpenHours() {

    publishTwice(hoursGenerated());
    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of(databases, calculus));
    // The first hour was booked meanwhile: booking no longer has it open.
    when(booking.openHoursOf(eq(tutor), any())).thenReturn(open(1, 2));

    publishTwice(new SkillEnabled(UPC, tutor, calculus, Instant.now()));

    assertThat(blocksOffered(calculus)).containsExactly(block(1), block(2));
    assertThat(blocksOffered(databases)).containsExactly(block(0), block(1), block(2));
  }

  @Test
  @DisplayName("a SkillEnabled heard after the course was withdrawn offers nothing")
  void aLateSkillEnabledOffersNothing() {

    when(booking.openHoursOf(eq(tutor), any())).thenReturn(open(0, 1, 2));

    publishTwice(new SkillEnabled(UPC, tutor, calculus, Instant.now()));

    assertThat(blocksOffered(calculus)).isEmpty();
  }

  @Test
  @DisplayName("SkillWithdrawn removes that course in every hour, and only that course")
  void aWithdrawnCourseLeavesTheSearch() {

    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of(databases, calculus));
    publishTwice(hoursGenerated());

    publishTwice(new SkillWithdrawn(UPC, tutor, calculus, Instant.now()));

    assertThat(blocksOffered(calculus)).isEmpty();
    assertThat(blocksOffered(databases)).hasSize(3);
  }

  @Test
  @DisplayName("HoursWithdrawn removes those hours for every course")
  void withdrawnHoursLeaveTheSearch() {

    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of(databases, calculus));
    publishTwice(hoursGenerated());

    publishTwice(new HoursWithdrawn(UPC, tutor, List.of(block(0), block(2)), Instant.now()));

    assertThat(blocksOffered(databases)).containsExactly(block(1));
    assertThat(blocksOffered(calculus)).containsExactly(block(1));
  }

  @Test
  @DisplayName("BookingConfirmed removes the booked hours for every course")
  void bookedHoursLeaveTheSearch() {

    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of(databases, calculus));
    publishTwice(hoursGenerated());

    publishTwice(
        new BookingConfirmed(
            UPC,
            UUID.randomUUID(),
            UUID.randomUUID(),
            tutor,
            databases,
            tomorrow,
            tomorrow.plus(Duration.ofHours(2)),
            List.of(block(0), block(1)),
            Credits.of(2),
            Instant.now()));

    assertThat(blocksOffered(databases)).containsExactly(block(2));
    assertThat(blocksOffered(calculus)).containsExactly(block(2));
  }

  @Test
  @DisplayName("BookingCancelled offers again the released hours booking still has open")
  void releasedHoursReturnToTheSearch() {

    publishTwice(hoursGenerated());
    publishTwice(
        new BookingConfirmed(
            UPC,
            UUID.randomUUID(),
            UUID.randomUUID(),
            tutor,
            databases,
            tomorrow,
            tomorrow.plus(Duration.ofHours(2)),
            List.of(block(0), block(1)),
            Credits.of(2),
            Instant.now()));
    // Booking put the first hour back in circulation and kept the second one out.
    when(booking.openHoursOf(eq(tutor), any())).thenReturn(open(0, 2));

    publishTwice(
        new BookingCancelled(
            UPC,
            UUID.randomUUID(),
            UUID.randomUUID(),
            tutor,
            List.of(block(0), block(1)),
            BookingCancelled.CancelledBy.STUDENT,
            false,
            Instant.now()));

    assertThat(blocksOffered(databases)).containsExactly(block(0), block(2));
  }

  @Test
  @DisplayName("a student's rating refreshes the tutor's standing in that course, in every hour")
  void aStudentsRatingRefreshesTheStanding() {

    when(skills.enabledSkillsOf(tutor)).thenReturn(List.of(databases, calculus));
    publishTwice(hoursGenerated());
    when(reputation.standingOf(tutor, calculus))
        .thenReturn(Optional.of(standing(calculus, 3, 3, "4.33")));

    publishTwice(
        new SessionRated(
            UPC,
            UUID.randomUUID(),
            tutor,
            calculus,
            SessionRated.Direction.STUDENT_TO_TUTOR,
            4,
            Instant.now()));

    assertThat(offersOfTheTutor())
        .filteredOn(row -> row.get("catalog_item_id").equals(calculus))
        .hasSize(3)
        .allSatisfy(
            row -> {
              assertThat((BigDecimal) row.get("average_stars")).isEqualByComparingTo("4.33");
              assertThat(row.get("ratings_count")).isEqualTo(3);
              assertThat(row.get("sessions_taught")).isEqualTo(3);
            });
    assertThat(offersOfTheTutor())
        .filteredOn(row -> row.get("catalog_item_id").equals(databases))
        .allSatisfy(
            row -> assertThat((BigDecimal) row.get("average_stars")).isEqualByComparingTo("4.75"));
  }

  @Test
  @DisplayName("a tutor rating a student changes nothing anybody searches by")
  void aTutorsRatingChangesNothing() {

    publishTwice(hoursGenerated());
    when(reputation.standingOf(tutor, databases))
        .thenReturn(Optional.of(standing(databases, 9, 9, "1.00")));

    publishTwice(
        new SessionRated(
            UPC,
            UUID.randomUUID(),
            tutor,
            databases,
            SessionRated.Direction.TUTOR_TO_STUDENT,
            null,
            Instant.now()));

    assertThat(offersOfTheTutor())
        .allSatisfy(
            row -> assertThat((BigDecimal) row.get("average_stars")).isEqualByComparingTo("4.75"));
  }
}
