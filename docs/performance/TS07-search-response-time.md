# TS07 — Response time of the search

How fast `GET /api/v1/search/offers` answers, measured on the real search, how the measurement is
repeated, and what it found. The measurement is a test, `SearchResponseTimeTest`, that runs with
every `./mvnw verify` and fails the build when the search leaves the thresholds below.

## What TS07 asks

From the Trello card of TS07:

| Scenario | Threshold |
|---|---|
| 1. Normal use | with fifty students using the platform at once, 95 % of the searches answered in less than **500 ms** |
| 2. Worst case | at the same concurrency, **no** answer above **2 s** |
| 3. A wide catalogue | a university with more than **three hundred courses** and **a hundred active tutors** keeps both thresholds |
| 4. Reproducible | the load test can be run again and gives a report of the times measured |

## Method

**What is timed.** The search as a student uses it: an HTTP request from outside the application
to `GET /api/v1/search/offers` with a real session token, through the access filter (TS03), the
controller, `SearchOffersUseCase` and PostgreSQL, until the whole JSON answer is read. The time is
taken by the client, so it includes everything the student would wait for except the network.

**How the application runs.** `@SpringBootTest` on a random port, the same application
`docker compose` starts. It connects to PostgreSQL 16 (Testcontainers) as `ayni_app`, with row
level security on (TS01), and with the pool of ten connections HikariCP has by default
(`spring.datasource.hikari.maximum-pool-size=10`; the other tests use twenty). Fifty students share
those ten connections, as they would in a deployment of one instance.

**The data** (scenario 3). `matching.available_offers` holds **five universities**, so the one
searched is not alone in the table, each with:

- **320 courses** and **120 tutors**; each tutor teaches 4 courses, chosen so that a few courses
  have many tutors and most have few or none, as a catalogue does (a uniform number squared picks
  the course);
- every tutor free three days a week, five consecutive hours of the day in Lima, for the next
  **four weeks**; one hour is one row per course the tutor teaches, as the projection keeps it;
- seven tutors in ten with an average and up to forty ratings, the rest new.

That is **28,800 offers per university, 144,000 in the table**. The most taught course of the
university searched has about 255 offers in a week. The rows are written by one `INSERT ... SELECT`
per university, from a fixed seed, so every run searches the same shape of data. They are not
produced by the events that feed the projection in production: those are proven by
`OfferProjectionListenersTest` and `SearchOffersEndToEndTest`, and reaching this volume event by
event would take minutes; what is measured here is the read. `ANALYZE` runs after the seed.

**The load** (scenarios 1 and 2). After 200 searches to warm the JVM up, 100 searches of one
student alone give the baseline. Then **50 students**, each with their own token, start together
behind a `CyclicBarrier` and run **20 searches each, back to back**, 1,000 in all, always page 0
of 20. The searches follow the proportions of real use:

| Kind | Share | What it asks | What answers it |
|---|---|---|---|
| `WEEK` | 70 % | a course, picked with the same skew as the tutors (popular courses are searched more), a week from some day of the next seven | the window, an exact match for the courses anybody teaches |
| `NARROW` | 20 % | the same courses, two hours at 03:00 in Lima, when nobody teaches | nothing inside, so the nearest hours before and after |
| `UNTAUGHT` | 10 % | a course nobody teaches | nothing, after both nearest queries |

**What fails the build.** One test per scenario, with the names of
`TS07-search-response-time.feature`; the load runs once for the class and each test judges it:

| Scenario | Fails when |
|---|---|
| Response time under normal use | an answer is not `200`, or the 95th percentile is 500 ms or more |
| Response time in the worst case | the slowest answer is 2 s or more |
| Behaviour with a wide catalogue | the university searched has 300 courses or fewer, or fewer than 100 tutors; any kind of search leaves either threshold; or any of the four queries of the search (the window, its count, the nearest before and the nearest after), explained with `EXPLAIN (ANALYZE, BUFFERS)` as `ayni_app` with the university of the transaction set, stops using `ix_available_offers_search` or reads the table with a `Seq Scan` |
| Reproducible measurement | the projection is not exactly the 144,000 offers the seed defines, or a line of the report is missing |

**Repeating it** (scenario 4).

```bash
cd backend && ./mvnw test -Dtest=SearchResponseTimeTest
```

The report is in the log, one line per figure, every line starting with `TS07`: the size of the
projection, the baseline, the 1,000 searches together and each kind apart (median, p95, p99, max),
and the four plans. `./mvnw verify` runs it too.

## Results

Measured on 2026-10-15 on the branch `feature/TS07-search-response-time-v2`.

**A laptop with 10 cores** (Apple M5, Docker 29), three runs:

| | Searches | Median | p95 | p99 | Max |
|---|---|---|---|---|---|
| One student alone | 100 | 4 ms | 5 to 6 ms | 6 to 8 ms | 6 to 12 ms |
| 50 students at once | 1,000 | 55 to 56 ms | **89 to 93 ms** | 107 to 119 ms | **136 to 144 ms** |
| — `WEEK` | 700 | 54 to 55 ms | 88 to 93 ms | 104 to 117 ms | 129 to 144 ms |
| — `NARROW` | 200 | 56 to 58 ms | 90 to 94 ms | 100 to 123 ms | 125 to 136 ms |
| — `UNTAUGHT` | 100 | 56 to 57 ms | 86 to 94 ms | 100 to 126 ms | 118 to 128 ms |

Inside the whole `./mvnw verify`, with the contexts of the other tests still alive in the same JVM,
the fifty students measured p95 117 ms and a slowest answer of 188 ms.

**The same test inside a container limited to two CPUs** (`docker run --cpus=2`, Maven and the
application inside it), to see what a small CI runner would measure:

| | Searches | Median | p95 | p99 | Max |
|---|---|---|---|---|---|
| One student alone | 100 | 7 ms | 8 ms | 8 ms | 9 ms |
| 50 students at once | 1,000 | 179 ms | **290 ms** | 328 ms | **492 ms** |

Both thresholds hold with room in every run: p95 is a fifth of the limit on the laptop and about
three fifths on two CPUs, and the slowest answer is a quarter of the 2 s limit on two CPUs.

**The plans** (as `ayni_app`, the most taught course, a week ahead):

| Query | Plan | Rows read | Execution |
|---|---|---|---|
| The window, page of 20 | `Index Scan using ix_available_offers_search`, then `Incremental Sort` on `average_stars DESC NULLS LAST, tutor_name, tutor_id` with `starts_at` presorted, `Limit` | 22 | 0.07 ms |
| Its count | `Index Scan using ix_available_offers_search`, `Aggregate` | 255 | 0.14 ms |
| Nearest before | `Index Scan Backward using ix_available_offers_search`, `Limit` | 20 | 0.04 ms |
| Nearest after | `Index Scan using ix_available_offers_search`, `Limit` | 20 | 0.04 ms |

All four use the three columns of the index as `Index Cond` (`tenant_id`, `catalog_item_id` and the
range of `starts_at`); `tutor_id <> student` is the only `Filter`, applied to rows the index already
narrowed. The policy of row level security appears as a `One-Time Filter` on `app.tenant_id`,
evaluated once per query and not per row. No plan reads the table sequentially.

## What it means

- **No index was added.** `ix_available_offers_search (tenant_id, catalog_item_id, starts_at)`,
  created with the table, already serves the four queries; the sort that follows the index only
  orders the rows of one hour and costs nothing measurable. A wider index would make every write of
  the projection dearer to save less than a tenth of a millisecond. Nothing needed a migration.
- **The time is not in the database.** A search alone takes 4 ms end to end and its queries less
  than half a millisecond together. Under fifty students at once the median grows to 55 ms on ten
  cores and 179 ms on two because fifty requests share the processors and ten connections: the
  growth follows the CPUs, not the data. The rest of a request (reading the session token, binding
  the university to each transaction for TS01, Hibernate, writing the JSON) is where the time goes;
  this measurement does not split it further.
- **What would change the answer.** A university much larger than scenario 3 (tens of thousands of
  offers in one course a week), or a search that pages deep into a popular course: the count of
  the window reads every offer of the week, which is cheap at 255 rows and grows linearly. The test
  is the alarm: it fails the build before a student notices.

## Limits of the measurement

- The client runs in the same JVM as the application and shares its CPUs, which makes the figures
  under load pessimistic rather than optimistic.
- One instance of the application and one PostgreSQL on the same machine: there is no network
  between them, as there is none under `docker compose`.
- The projection is seeded, not fed by events; its shape follows the rules of the projection (one
  row per hour and course, standings copied) but its contents are generated.
