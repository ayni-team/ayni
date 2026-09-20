package pe.ayni.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.services.BlockGenerator;
import pe.ayni.booking.infrastructure.AvailabilityExceptionRepository;
import pe.ayni.booking.infrastructure.AvailabilityPatternRepository;
import pe.ayni.booking.infrastructure.AvailabilityPauseRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.shared.events.HoursGenerated;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Generating the same horizon twice must not offer the same hour twice.
 *
 * <p>It happens for real: a tutor changing anything regenerates their availability, and a
 * scheduled job walks the same days again the following night.
 */
class GenerateHourBlocksUseCaseTest {

  private static final String UPC = "UPC";
  private static final UUID TUTOR = UUID.randomUUID();
  private static final ZoneId LIMA = ZoneId.of("America/Lima");
  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
  /** A Monday. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

  private final AvailabilityPatternRepository patterns = mock(AvailabilityPatternRepository.class);
  private final AvailabilityExceptionRepository exceptions =
      mock(AvailabilityExceptionRepository.class);
  private final AvailabilityPauseRepository pauses = mock(AvailabilityPauseRepository.class);
  private final HourBlockRepository blocks = mock(HourBlockRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

  private final GenerateHourBlocksUseCase useCase =
      new GenerateHourBlocksUseCase(
          patterns,
          exceptions,
          pauses,
          blocks,
          new BlockGenerator(),
          events,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private void givenAMorningPattern() {
    when(patterns.findActiveInHorizon(any(), any(), any(), any()))
        .thenReturn(
            List.of(
                new AvailabilityPattern(
                    UUID.randomUUID(),
                    UPC,
                    TUTOR,
                    DayOfWeek.MONDAY,
                    LocalTime.of(9, 0),
                    LocalTime.of(12, 0),
                    MONDAY.minusMonths(1),
                    null,
                    NOW)));
    when(exceptions.findWithinHorizon(any(), any(), any(), any())).thenReturn(List.of());
    when(pauses.findOverlappingPauses(any(), any(), any(), any())).thenReturn(List.of());
  }

  private void generate() {
    TenantContext.runAs(UPC, () -> useCase.execute(TUTOR, MONDAY, MONDAY, LIMA));
  }

  @SuppressWarnings("unchecked")
  private List<HourBlock> saved() {
    ArgumentCaptor<List<HourBlock>> captor = ArgumentCaptor.forClass(List.class);
    verify(blocks).saveAll(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("writes the hours the rules produce and says which ones appeared")
  void writesTheNewHours() {

    givenAMorningPattern();
    when(blocks.findWithin(any(), any(), any(), any())).thenReturn(List.of());

    generate();

    assertThat(saved()).hasSize(3);

    ArgumentCaptor<HoursGenerated> published = ArgumentCaptor.forClass(HoursGenerated.class);
    verify(events).publishEvent(published.capture());
    assertThat(published.getValue().blocks()).hasSize(3);
    assertThat(published.getValue().tenantId()).isEqualTo(UPC);
  }

  @Test
  @DisplayName("running it again over hours that already exist writes nothing and says nothing")
  void doesNotRepeatHoursThatAreAlreadyThere() {

    givenAMorningPattern();
    // What a first run left behind.
    when(blocks.findWithin(any(), any(), any(), any()))
        .thenReturn(
            List.of(
                blockAt(LocalTime.of(9, 0)),
                blockAt(LocalTime.of(10, 0)),
                blockAt(LocalTime.of(11, 0))));

    generate();

    verify(blocks, never()).saveAll(anyList());
    // No event either: matching would otherwise be told about hours it already knows.
    verify(events, never()).publishEvent(any(HoursGenerated.class));
  }

  @Test
  @DisplayName("only the hours that are missing are written")
  void writesOnlyWhatIsMissing() {

    givenAMorningPattern();
    when(blocks.findWithin(any(), any(), any(), any()))
        .thenReturn(List.of(blockAt(LocalTime.of(9, 0))));

    generate();

    assertThat(saved()).hasSize(2);
  }

  private static HourBlock blockAt(LocalTime time) {
    Instant startsAt = time.atDate(MONDAY).atZone(LIMA).toInstant();
    return new HourBlock(
        UUID.randomUUID(), UPC, TUTOR, startsAt, startsAt.plusSeconds(3600), null, NOW);
  }
}
