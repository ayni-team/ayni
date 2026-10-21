package pe.ayni.analytics.domain.model;

import java.util.List;

/**
 * What a coordinator reads for a period: the totals, the series (one entry per day) and the courses
 * students looked for without finding an hour.
 *
 * <p>The active students of the totals are the distinct people of the whole period, not the sum of
 * the days: somebody who took part on three days is one active student. The uncovered demand is
 * ordered from the most sought course to the least.
 */
public record UsageIndicators(
    IndicatorPeriod period,
    UsageFigures totals,
    List<DailyUsage> days,
    List<UncoveredCourse> uncoveredDemand) {}
