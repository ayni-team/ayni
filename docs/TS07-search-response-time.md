# TS07 — Search response time

## Method

Seeded `matching.available_offers` with 50,000 rows (500 tutors × 100 one-hour
blocks each, one course) and ran `EXPLAIN ANALYZE` against the three query
shapes `SearchAvailableOffersUseCase` issues:

1. The exact-range search (`findByTenantIdAndCourseIdAndStartsAtBetween...`)
2. The fallback "after" query
3. The fallback "before" query

Script: see `measure-search-performance.sql` (not committed — throwaway data,
run directly against the dev Postgres container and cleaned up afterwards).

## Results

| Query              | Plan                                              | Execution time |
|---------------------|----------------------------------------------------|----------------|
| Exact range          | Index Scan on `idx_available_offers_tenant_course_starts` + Incremental Sort for `tutor_rating DESC` | 2.89 ms |
| Fallback, after       | Index Scan on the same index                       | 0.10 ms        |
| Fallback, before      | Index Scan **Backward** on the same index           | 0.06 ms        |

No `Seq Scan` anywhere. The single composite index created in T1
(`tenant_id, course_id, starts_at`) already serves all three access patterns,
including descending order for the "before" fallback.

## Conclusion

No new index is needed at this data volume. The `Incremental Sort` on the
exact-range query (needed because the index doesn't cover `tutor_rating`) adds
negligible cost and is not worth the complexity of a wider index today —
revisit only if a course regularly has hundreds of tutors tied on the exact
same starting hour.