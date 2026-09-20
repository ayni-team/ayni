package pe.ayni.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** A tutor cannot be free twice over the same hour. */
class DeclareWeeklyAvailabilityUseCaseTest {

  private static final String UPC = "UPC";
  private static final UUID TUTOR = UUID.randomUUID();
  private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

  private final AvailabilityPatternRepository patterns = mock(AvailabilityPatternRepository.class);
  private final DeclareWeeklyAvailabilityUseCase useCase =
      new DeclareWeeklyAvailabilityUseCase(patterns, Clock.fixed(NOW, ZoneOffset.UTC));

  private AvailabilityPattern existing(LocalTime from, LocalTime to) {
    return new AvailabilityPattern(
        UUID.randomUUID(), UPC, TUTOR, DayOfWeek.MONDAY, from, to, FROM, null, NOW);
  }

  private AvailabilityPattern declare(LocalTime from, LocalTime to) {
    final AvailabilityPattern[] saved = new AvailabilityPattern[1];
    TenantContext.runAs(
        UPC,
        () ->
            saved[0] =
                useCase.execute(TUTOR, DayOfWeek.MONDAY, from, to, FROM, null));
    return saved[0];
  }

  @Test
  @DisplayName("refuses a window that runs into one the tutor already has")
  void refusesAnOverlappingWindow() {

    when(patterns.findByTenantIdAndTutorIdAndDayOfWeek(eq(UPC), eq(TUTOR), anyShort()))
        .thenReturn(List.of(existing(LocalTime.of(9, 0), LocalTime.of(12, 0))));

    assertThatThrownBy(() -> declare(LocalTime.of(11, 0), LocalTime.of(13, 0)))
        .isInstanceOf(BookingRuleViolation.class)
        .hasMessageContaining("overlaps");

    verify(patterns, never()).save(any());
  }

  @Test
  @DisplayName("accepts a window that starts where another ends")
  void acceptsAConsecutiveWindow() {

    when(patterns.findByTenantIdAndTutorIdAndDayOfWeek(eq(UPC), eq(TUTOR), anyShort()))
        .thenReturn(List.of(existing(LocalTime.of(9, 0), LocalTime.of(12, 0))));
    when(patterns.save(any())).thenAnswer(call -> call.getArgument(0));

    AvailabilityPattern declared = declare(LocalTime.of(12, 0), LocalTime.of(14, 0));

    assertThat(declared.getStartsAtTime()).isEqualTo(LocalTime.of(12, 0));
    verify(patterns).save(any());
  }

  @Test
  @DisplayName("the first window of a weekday has nothing to run into")
  void acceptsTheFirstWindow() {

    when(patterns.findByTenantIdAndTutorIdAndDayOfWeek(eq(UPC), eq(TUTOR), anyShort()))
        .thenReturn(List.of());
    when(patterns.save(any())).thenAnswer(call -> call.getArgument(0));

    assertThat(declare(LocalTime.of(9, 0), LocalTime.of(11, 0))).isNotNull();
  }

  @Test
  @DisplayName("a malformed window is refused before anything is read")
  void refusesAMalformedWindowWithoutQuerying() {

    assertThatThrownBy(() -> declare(LocalTime.of(12, 0), LocalTime.of(9, 0)))
        .isInstanceOf(BookingRuleViolation.class);

    verify(patterns, never()).findByTenantIdAndTutorIdAndDayOfWeek(any(), any(), anyShort());
  }
}
