package pe.ayni.analytics.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.analytics.domain.model.DailyIndicator;
import pe.ayni.analytics.domain.model.DailyUsage;
import pe.ayni.analytics.domain.model.IndicatorPeriod;
import pe.ayni.analytics.domain.model.UsageFigures;
import pe.ayni.analytics.domain.model.UsageIndicators;
import pe.ayni.analytics.infrastructure.ActiveStudentDayRepository;
import pe.ayni.analytics.infrastructure.DailyIndicatorRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What a coordinator reads of how much their university uses the service in a period: the totals
 * and the figures of every day.
 *
 * <p>It answers from the module's own tables, for the university of the request only, and returns
 * counts: nobody's name or identifier is read, and the tables that hold identifiers are only asked
 * how many distinct people there were. Days with no activity are in the series with zeros, so a
 * chart has no gaps to fill.
 */
@Service
public class ConsultIndicatorsUseCase {

  private final CoordinatorGuard guard;
  private final DailyIndicatorRepository indicators;
  private final ActiveStudentDayRepository activeDays;

  ConsultIndicatorsUseCase(
      CoordinatorGuard guard,
      DailyIndicatorRepository indicators,
      ActiveStudentDayRepository activeDays) {
    this.guard = guard;
    this.indicators = indicators;
    this.activeDays = activeDays;
  }

  /**
   * @param from first day of the period, included
   * @param to last day of the period, included
   * @throws pe.ayni.analytics.domain.model.IndicatorsAccessDenied when the person is not an active
   *     coordinator of the university
   * @throws pe.ayni.analytics.domain.model.InvalidPeriod when the period is inverted or too long
   */
  @Transactional(readOnly = true)
  public UsageIndicators execute(UUID coordinatorId, LocalDate from, LocalDate to) {
    String tenantId = TenantContext.require();
    guard.require(coordinatorId);
    IndicatorPeriod period = new IndicatorPeriod(from, to);

    Map<LocalDate, DailyIndicator> recorded =
        indicators.findByTenantIdAndDayBetweenOrderByDay(tenantId, from, to).stream()
            .collect(Collectors.toMap(DailyIndicator::getDay, Function.identity()));
    List<DailyUsage> days = new ArrayList<>();
    long sessions = 0;
    long hours = 0;
    long newStudents = 0;
    long recognitions = 0;
    for (LocalDate day : period.days()) {
      DailyIndicator row = recorded.get(day);
      UsageFigures figures =
          row == null
              ? UsageFigures.NONE
              : new UsageFigures(
                  row.getSessionsCompleted(),
                  row.getHoursExchanged(),
                  row.getActiveStudents(),
                  row.getNewStudents(),
                  row.getRequestsApproved());
      sessions += figures.sessionsCompleted();
      hours += figures.hoursExchanged();
      newStudents += figures.newStudents();
      recognitions += figures.recognitionsGranted();
      days.add(new DailyUsage(day, figures));
    }
    long distinctActive = activeDays.countDistinctBetween(tenantId, from, to);
    return new UsageIndicators(
        period,
        new UsageFigures(sessions, hours, distinctActive, newStudents, recognitions),
        List.copyOf(days));
  }
}
