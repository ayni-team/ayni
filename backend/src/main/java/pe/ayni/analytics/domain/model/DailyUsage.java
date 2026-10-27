package pe.ayni.analytics.domain.model;

import java.time.LocalDate;

/** The figures of one day of the university. */
public record DailyUsage(LocalDate day, UsageFigures figures) {}
