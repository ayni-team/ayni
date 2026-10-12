package pe.ayni.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import pe.ayni.analytics.domain.services.UniversityDay;

class UniversityDayTest {

  private static final ZoneId LIMA = ZoneId.of("America/Lima");

  @Test
  void theDayIsTheOneOfTheUniversityNotUtc() {
    // 22:30 in Lima is already the next day in UTC.
    Instant lateEvening = Instant.parse("2026-10-06T03:30:00Z");

    assertThat(UniversityDay.of(lateEvening, LIMA)).isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(UniversityDay.of(lateEvening, ZoneId.of("UTC"))).isEqualTo(LocalDate.of(2026, 10, 6));
  }

  @Test
  void aZoneAheadOfUtcMovesTheDayForward() {
    Instant lateEvening = Instant.parse("2026-10-05T20:00:00Z");

    assertThat(UniversityDay.of(lateEvening, ZoneId.of("Asia/Tokyo"))).isEqualTo(LocalDate.of(2026, 10, 6));
  }

  @Test
  void midnightInTheUniversityStartsTheNewDay() {
    assertThat(UniversityDay.of(Instant.parse("2026-10-06T04:59:59Z"), LIMA))
        .isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(UniversityDay.of(Instant.parse("2026-10-06T05:00:00Z"), LIMA))
        .isEqualTo(LocalDate.of(2026, 10, 6));
  }
}
