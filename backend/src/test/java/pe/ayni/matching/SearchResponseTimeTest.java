package pe.ayni.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Array;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * TS07: the real search, {@code GET /api/v1/search/offers}, timed from outside the application.
 *
 * <p>The method and the figures are in {@code docs/performance/TS07-search-response-time.md}; this
 * test is the measurement that document describes, so it can be repeated by anybody with {@code
 * ./mvnw verify} and fails the build when the search leaves the thresholds the story sets.
 *
 * <p>What it measures: fifty signed in students at once, each with their own session token, over
 * real HTTP to the running application, which connects as {@code ayni_app} with row level
 * security on and the pool of connections the application has by default. The projection holds
 * five universities of the size scenario 3 asks for, more than three hundred courses and a hundred
 * tutors each, so the university searched is not alone in the table.
 *
 * <p>The projection is seeded with one statement per university rather than through the events
 * that feed it in production: the events are proven elsewhere ({@code OfferProjectionListenersTest},
 * {@code SearchOffersEndToEndTest}), and what is measured here is the read path at a volume that
 * would take minutes to reach event by event. The data is generated from a fixed seed, so every run
 * searches the same projection.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "ayni.test.identity-headers=false",
      // The pool the application has outside the tests, so fifty students share ten connections
      // as they would under docker compose.
      "spring.datasource.hikari.maximum-pool-size=10"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchResponseTimeTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = MatchingTestDatabase.INSTANCE;

  private static final Logger log = LoggerFactory.getLogger(SearchResponseTimeTest.class);

  /** TS07, scenario 1: 95 % of the answers in less than this. */
  static final Duration P95_THRESHOLD = Duration.ofMillis(500);

  /** TS07, scenario 2: no answer slower than this. */
  static final Duration MAX_THRESHOLD = Duration.ofSeconds(2);

  static final int UNIVERSITIES = 5;
  static final int COURSES = 320;
  static final int TUTORS = 120;
  static final int COURSES_PER_TUTOR = 4;
  static final int DAYS_AHEAD = 28;
  static final int STUDENTS = 50;
  static final int SEARCHES_PER_STUDENT = 20;
  static final int WARM_UP_SEARCHES = 200;
  static final long SEED = 20261015L;

  private static final ZoneId LIMA = ZoneId.of("America/Lima");

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transactions;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final List<String> universities = new ArrayList<>();
  private final List<Person> students = new ArrayList<>();
  /** The courses of the university searched, the most taught first. */
  private final List<UUID> courses = new ArrayList<>();

  private String searched;
  private long seededRows;
  /** The 1,000 searches of the fifty students, measured once and judged by each scenario. */
  private List<Sample> measured;
  private final List<String> report = new ArrayList<>();

  // ---------------------------------------------------------------------------------------------
  // The projection
  // ---------------------------------------------------------------------------------------------

  @BeforeAll
  void fiveUniversitiesOfRealisticSize() {
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    Random random = new Random(SEED);
    searched = people.aUniversity("P");
    universities.add(searched);
    for (int i = 1; i < UNIVERSITIES; i++) {
      universities.add("Q" + i + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
    }
    Instant started = Instant.now();
    long rows = 0;
    for (String university : universities) {
      List<UUID> itsCourses = new ArrayList<>();
      for (int c = 0; c < COURSES; c++) {
        itsCourses.add(UUID.randomUUID());
      }
      if (university.equals(searched)) {
        courses.addAll(itsCourses);
      }
      rows += seed(university, itsCourses, random);
    }
    seededRows = rows;
    jdbc.execute("analyze matching.available_offers");
    for (int s = 0; s < STUDENTS; s++) {
      students.add(people.aStudent(searched));
    }
    report(
        "projection: %d universities, %d offers in all, seeded in %d ms"
            .formatted(UNIVERSITIES, rows, Duration.between(started, Instant.now()).toMillis()));
  }

  @AfterAll
  void removeTheProjectionSeeded() {
    for (String university : universities) {
      jdbc.update("delete from matching.available_offers where tenant_id = ?", university);
    }
  }

  /**
   * One university: {@code TUTORS} tutors who teach {@code COURSES_PER_TUTOR} courses each, chosen
   * so a few courses have many tutors and most have few or none, as a catalogue does. Each tutor is
   * free three days of every week, five consecutive hours of the day in Lima, for the next four
   * weeks. One block of a tutor is one row per course they teach, as the projection keeps it.
   */
  private int seed(String university, List<UUID> itsCourses, Random random) {
    int pairs = TUTORS * COURSES_PER_TUTOR;
    UUID[] tutorIds = new UUID[pairs];
    UUID[] itemIds = new UUID[pairs];
    String[] names = new String[pairs];
    BigDecimal[] stars = new BigDecimal[pairs];
    Integer[] ratings = new Integer[pairs];
    Integer[] taught = new Integer[pairs];
    Integer[] tutorNumbers = new Integer[pairs];
    int pair = 0;
    for (int t = 0; t < TUTORS; t++) {
      UUID tutor = UUID.randomUUID();
      List<Integer> chosen = new ArrayList<>();
      while (chosen.size() < COURSES_PER_TUTOR) {
        // Squaring a uniform number piles the choices at the start of the catalogue.
        int course = (int) Math.floor(COURSES * Math.pow(random.nextDouble(), 2));
        if (!chosen.contains(course)) {
          chosen.add(course);
        }
      }
      for (int course : chosen) {
        boolean isNew = random.nextInt(10) < 3;
        int ratingsCount = isNew ? random.nextInt(3) : 3 + random.nextInt(40);
        tutorIds[pair] = tutor;
        itemIds[pair] = itsCourses.get(course);
        names[pair] = "Tutor " + t;
        stars[pair] = isNew ? null : BigDecimal.valueOf(300 + random.nextInt(201), 2);
        ratings[pair] = ratingsCount;
        taught[pair] = ratingsCount + random.nextInt(5);
        tutorNumbers[pair] = t;
        pair++;
      }
    }
    return jdbc.execute(
        (java.sql.Connection connection) -> {
          try (var statement =
              connection.prepareStatement(
                  """
                  insert into matching.available_offers
                    (tenant_id, block_id, tutor_id, catalog_item_id, starts_at, tutor_name,
                     average_stars, ratings_count, sessions_taught)
                  select ?, md5(pair.tutor_id::text || hour::text)::uuid, pair.tutor_id,
                         pair.item_id, hour, pair.name, pair.stars, pair.ratings, pair.taught
                  from unnest(?::uuid[], ?::uuid[], ?::text[], ?::numeric[], ?::int[], ?::int[],
                              ?::int[])
                       as pair(tutor_id, item_id, name, stars, ratings, taught, tutor_number)
                  cross join lateral generate_series(
                      date_trunc('hour', now()) + interval '1 hour',
                      date_trunc('hour', now()) + make_interval(days => ?),
                      interval '1 hour') as hour
                  where (extract(isodow from hour at time zone 'America/Lima')::int
                         + pair.tutor_number) % 7 < 3
                    and extract(hour from hour at time zone 'America/Lima')::int
                        between 8 + pair.tutor_number % 10 and 12 + pair.tutor_number % 10
                  """)) {
            statement.setString(1, university);
            statement.setArray(2, array(connection, "uuid", tutorIds));
            statement.setArray(3, array(connection, "uuid", itemIds));
            statement.setArray(4, array(connection, "text", names));
            statement.setArray(5, array(connection, "numeric", stars));
            statement.setArray(6, array(connection, "int4", ratings));
            statement.setArray(7, array(connection, "int4", taught));
            statement.setArray(8, array(connection, "int4", tutorNumbers));
            statement.setInt(9, DAYS_AHEAD);
            return statement.executeUpdate();
          }
        });
  }

  private static Array array(java.sql.Connection connection, String type, Object[] values)
      throws java.sql.SQLException {
    return connection.createArrayOf(type, values);
  }

  // ---------------------------------------------------------------------------------------------
  // The searches
  // ---------------------------------------------------------------------------------------------

  /** What a student asks for, in the proportions the measurement uses. */
  enum Kind {
    /** A week from some day of the next seven, the usual search: an exact match for most courses. */
    WEEK,
    /** Two hours in the small hours of Lima, when nobody teaches: the nearest hours outside it. */
    NARROW,
    /** A course of the catalogue nobody teaches: an empty answer after both nearest queries. */
    UNTAUGHT
  }

  record Sample(Kind kind, int status, long millis) {}

  private Kind kindOf(int index) {
    int bucket = index % 10;
    return bucket < 7 ? Kind.WEEK : bucket < 9 ? Kind.NARROW : Kind.UNTAUGHT;
  }

  private Sample search(Person student, Kind kind, Random random) throws Exception {
    UUID course =
        switch (kind) {
          case UNTAUGHT -> UUID.randomUUID();
          // Students search the popular courses more, as tutors teach them more.
          default -> courses.get((int) Math.floor(COURSES * Math.pow(random.nextDouble(), 2)));
        };
    LocalDate day = LocalDate.now(LIMA).plusDays(random.nextInt(7));
    Instant from;
    Instant to;
    if (kind == Kind.NARROW) {
      from = day.atTime(3, 0).atZone(LIMA).toInstant();
      to = from.plus(Duration.ofHours(2));
    } else {
      from = day.atStartOfDay(LIMA).toInstant();
      to = from.plus(Duration.ofDays(7));
    }
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:%d/api/v1/search/offers?catalogItemId=%s&from=%s&to=%s&page=0&size=20"
                        .formatted(port, course, from, to)))
            .header("Authorization", "Bearer " + student.token())
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
    long sent = System.nanoTime();
    HttpResponse<String> answer = http.send(request, HttpResponse.BodyHandlers.ofString());
    long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sent);
    return new Sample(kind, answer.statusCode(), millis);
  }

  /**
   * The load of scenarios 1 and 2, run once for the class: a warm up, one student alone for
   * comparison, then the fifty students at once behind a barrier.
   */
  private synchronized List<Sample> measured() throws Exception {
    if (measured != null) {
      return measured;
    }
    // Warm up: the first requests of a JVM measure the compiler, not the search.
    Random warmUp = new Random(SEED + 1);
    for (int i = 0; i < WARM_UP_SEARCHES; i++) {
      search(students.get(i % STUDENTS), kindOf(i), warmUp);
    }
    // One student alone, for comparison: what the concurrency adds is the difference.
    Random alone = new Random(SEED + 2);
    List<Sample> baseline = new ArrayList<>();
    for (int i = 0; i < SEARCHES_PER_STUDENT * 5; i++) {
      baseline.add(search(students.getFirst(), kindOf(i), alone));
    }
    report("one student alone: " + summary(baseline));

    CyclicBarrier together = new CyclicBarrier(STUDENTS);
    ExecutorService clients = Executors.newFixedThreadPool(STUDENTS);
    List<Future<List<Sample>>> running = new ArrayList<>();
    for (int s = 0; s < STUDENTS; s++) {
      Person student = students.get(s);
      Random random = new Random(SEED + 100 + s);
      int offset = s;
      running.add(
          clients.submit(
              () -> {
                together.await();
                List<Sample> mine = new ArrayList<>();
                for (int i = 0; i < SEARCHES_PER_STUDENT; i++) {
                  mine.add(search(student, kindOf(offset + i), random));
                }
                return mine;
              }));
    }
    List<Sample> samples = new ArrayList<>();
    for (Future<List<Sample>> each : running) {
      samples.addAll(each.get(5, TimeUnit.MINUTES));
    }
    clients.shutdown();

    Map<Kind, List<Sample>> byKind = new EnumMap<>(Kind.class);
    samples.forEach(sample -> byKind.computeIfAbsent(sample.kind(), kind -> new ArrayList<>()).add(sample));
    report("all: " + summary(samples));
    byKind.forEach((kind, these) -> report(kind + ": " + summary(these)));
    measured = List.copyOf(samples);
    return measured;
  }

  @Test
  @DisplayName("Response time under normal use")
  void responseTimeUnderNormalUse() throws Exception {
    List<Sample> samples = measured();

    assertThat(samples).hasSize(STUDENTS * SEARCHES_PER_STUDENT);
    assertThat(samples).extracting(Sample::status).containsOnly(200);
    assertThat(percentile(samples, 95)).isLessThan(P95_THRESHOLD.toMillis());
  }

  @Test
  @DisplayName("Response time in the worst case")
  void responseTimeInTheWorstCase() throws Exception {
    List<Sample> samples = measured();

    assertThat(samples).extracting(Sample::status).containsOnly(200);
    assertThat(percentile(samples, 100)).isLessThan(MAX_THRESHOLD.toMillis());
  }

  @Test
  @DisplayName("Behaviour with a wide catalogue")
  void behaviourWithAWideCatalogue() throws Exception {
    // The university searched has the catalogue of scenario 3, and is not alone in the table.
    assertThat(courses).hasSizeGreaterThan(300);
    assertThat(
            jdbc.queryForObject(
                "select count(distinct tutor_id) from matching.available_offers where tenant_id = ?",
                Long.class,
                searched))
        .isGreaterThanOrEqualTo(100);
    assertThat(
            jdbc.queryForObject(
                "select count(distinct tenant_id) from matching.available_offers where tenant_id = any(?)",
                Long.class,
                (Object) universities.toArray(String[]::new)))
        .isEqualTo(UNIVERSITIES);
    // Both thresholds hold for every kind of search over it, the empty ones included.
    List<Sample> samples = measured();
    for (Kind kind : Kind.values()) {
      List<Sample> these = samples.stream().filter(sample -> sample.kind() == kind).toList();
      assertThat(these).as(kind.name()).isNotEmpty();
      assertThat(percentile(these, 95)).as(kind.name()).isLessThan(P95_THRESHOLD.toMillis());
      assertThat(percentile(these, 100)).as(kind.name()).isLessThan(MAX_THRESHOLD.toMillis());
    }
    // And the search reads its index whatever the size of the table.
    theSearchQueriesReadTheSearchIndex();
  }

  @Test
  @DisplayName("Reproducible measurement")
  void reproducibleMeasurement() throws Exception {
    measured();
    // The same projection every run: four whole weeks of hours, so every tutor has the same
    // sixty hours whatever the day the build runs, from a fixed seed.
    assertThat(seededRows)
        .isEqualTo((long) UNIVERSITIES * TUTORS * COURSES_PER_TUTOR * 3 * 5 * (DAYS_AHEAD / 7));
    // The report has every figure the document quotes, in the log, every line starting with TS07.
    assertThat(report)
        .anyMatch(line -> line.startsWith("projection: "))
        .anyMatch(line -> line.startsWith("one student alone: "))
        .anyMatch(line -> line.startsWith("all: 1000 searches, median "))
        .anyMatch(line -> line.startsWith("WEEK: "))
        .anyMatch(line -> line.startsWith("NARROW: "))
        .anyMatch(line -> line.startsWith("UNTAUGHT: "));
  }

  private void report(String line) {
    report.add(line);
    log.info("TS07 {}", line);
  }

  /** The four queries of the search read the search index, never the whole table. */
  private void theSearchQueriesReadTheSearchIndex() {
    // The most taught course, the one with the most offers to sort.
    UUID popular =
        jdbc.queryForObject(
            """
            select catalog_item_id from matching.available_offers where tenant_id = ?
            group by catalog_item_id order by count(*) desc limit 1
            """,
            UUID.class,
            searched);
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant from = LocalDate.now(LIMA).atStartOfDay(LIMA).toInstant();
    Instant to = from.plus(Duration.ofDays(7));
    UUID student = students.getFirst().id();

    String within =
        explain(
            """
            select * from matching.available_offers
            where tenant_id = ? and catalog_item_id = ? and tutor_id <> ? and starts_at > ?
              and starts_at >= ? and starts_at < ?
            order by starts_at, average_stars desc nulls last, tutor_name, tutor_id
            offset 0 limit 20
            """,
            popular, student, now, from, to);
    String counted =
        explain(
            """
            select count(*) from matching.available_offers
            where tenant_id = ? and catalog_item_id = ? and tutor_id <> ? and starts_at > ?
              and starts_at >= ? and starts_at < ?
            """,
            popular, student, now, from, to);
    String before =
        explain(
            """
            select * from matching.available_offers
            where tenant_id = ? and catalog_item_id = ? and tutor_id <> ? and starts_at > ?
              and starts_at < ?
            order by starts_at desc limit 20
            """,
            popular, student, now, to);
    String after =
        explain(
            """
            select * from matching.available_offers
            where tenant_id = ? and catalog_item_id = ? and tutor_id <> ? and starts_at > ?
              and starts_at >= ?
            order by starts_at limit 20
            """,
            popular, student, now, from);

    for (String plan : List.of(within, counted, before, after)) {
      assertThat(plan).contains("ix_available_offers_search").doesNotContain("Seq Scan");
    }
  }

  /**
   * The plan of a query as the application runs it: as {@code ayni_app}, with the university of
   * the transaction set, so the policy of row level security is part of the plan.
   */
  private String explain(String sql, Object... arguments) {
    Object[] withTenant = new Object[arguments.length + 1];
    withTenant[0] = searched;
    System.arraycopy(arguments, 0, withTenant, 1, arguments.length);
    for (int i = 0; i < withTenant.length; i++) {
      if (withTenant[i] instanceof Instant instant) {
        withTenant[i] = java.sql.Timestamp.from(instant);
      }
    }
    String plan =
        transactions.execute(
            status -> {
              jdbc.execute("set local role ayni_app");
              jdbc.queryForObject(
                  "select set_config('app.tenant_id', ?, true)", String.class, searched);
              return String.join(
                  "\n",
                  jdbc.queryForList(
                      "explain (analyze, buffers) " + sql, String.class, withTenant));
            });
    report("plan:\n" + plan);
    return plan;
  }

  private static long percentile(List<Sample> samples, int percentile) {
    List<Long> sorted = samples.stream().map(Sample::millis).sorted().toList();
    int index = (int) Math.ceil(sorted.size() * percentile / 100.0) - 1;
    return sorted.get(Math.max(index, 0));
  }

  private static String summary(List<Sample> samples) {
    return "%d searches, median %d ms, p95 %d ms, p99 %d ms, max %d ms"
        .formatted(
            samples.size(),
            percentile(samples, 50),
            percentile(samples, 95),
            percentile(samples, 99),
            percentile(samples, 100));
  }
}
