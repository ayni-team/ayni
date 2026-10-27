package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The hour block queries US03 relies on, against PostgreSQL: the stretch a student asks for comes
 * back in one query and only from their university, the expired holds are found for the sweep, and
 * the version column really makes a stale write lose. Also the hours {@link BookingApi#openHoursOf}
 * hands to matching.
 */
@SpringBootTest
class HourBlockQueriesTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = BookingTestDatabase.INSTANCE;

  /** A university of its own, so the rows other tests leave behind cannot answer these queries. */
  private final String tenant = "T" + UUID.randomUUID().toString().substring(0, 8);

  private final String otherTenant = "T" + UUID.randomUUID().toString().substring(0, 8);
  private final UUID tutor = UUID.randomUUID();
  private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  private final Instant tomorrowAtNine = now.plus(Duration.ofDays(1));

  @Autowired private HourBlockRepository blocks;
  @Autowired private BookingApi bookingApi;

  private HourBlock hour(String tenantId, Instant start) {
    return new HourBlock(
        UUID.randomUUID(), tenantId, tutor, start, start.plus(Duration.ofHours(1)), null, now);
  }

  private Instant nineOClockPlus(int hours) {
    return tomorrowAtNine.plus(Duration.ofHours(hours));
  }

  @Test
  @DisplayName("the stretch asked for comes back ordered, half open, and only from its university")
  void findsTheStretchWithinItsUniversity() {

    blocks.saveAll(
        List.of(
            hour(tenant, nineOClockPlus(2)),
            hour(tenant, nineOClockPlus(0)),
            hour(tenant, nineOClockPlus(1)),
            hour(tenant, nineOClockPlus(3)),
            // The same tutor id and hour in another university is somebody else's hour.
            hour(otherTenant, nineOClockPlus(1))));

    List<HourBlock> found =
        blocks.findWithin(tenant, tutor, nineOClockPlus(0), nineOClockPlus(3));

    assertThat(found)
        .extracting(HourBlock::getStartsAt)
        .containsExactly(nineOClockPlus(0), nineOClockPlus(1), nineOClockPlus(2));
    assertThat(found).allMatch(block -> block.getTenantId().equals(tenant));
  }

  @Test
  @DisplayName("the sweep finds the holds that ran out, and nothing else")
  void findsOnlyTheExpiredHolds() {

    UUID student = UUID.randomUUID();
    Instant sixMinutesAgo = now.minus(Duration.ofMinutes(6));

    HourBlock expired = hour(tenant, nineOClockPlus(0));
    expired.hold(student, sixMinutesAgo);
    HourBlock alive = hour(tenant, nineOClockPlus(1));
    alive.hold(student, now);
    HourBlock free = hour(tenant, nineOClockPlus(2));
    HourBlock expiredElsewhere = hour(otherTenant, nineOClockPlus(0));
    expiredElsewhere.hold(student, sixMinutesAgo);

    blocks.saveAll(List.of(expired, alive, free, expiredElsewhere));

    assertThat(blocks.findExpiredHolds(tenant, now))
        .extracting(HourBlock::getId)
        .containsExactly(expired.getId());
  }

  @Test
  @DisplayName("a hold is not expired at the very instant it ends")
  void aHoldEndingNowIsNotExpired() {

    HourBlock block = hour(tenant, nineOClockPlus(0));
    block.hold(UUID.randomUUID(), now.minus(HourBlock.HOLD_DURATION));
    blocks.save(block);

    assertThat(blocks.findExpiredHolds(tenant, now)).isEmpty();
    assertThat(blocks.findExpiredHolds(tenant, now.plusSeconds(1))).hasSize(1);
  }

  @Test
  @DisplayName("of two writes made from the same version, the second one loses")
  void aStaleWriteLoses() {

    UUID id = blocks.save(hour(tenant, nineOClockPlus(0))).getId();

    // Two students read the same free hour, each in a request of their own.
    HourBlock readByAna = blocks.findById(id).orElseThrow();
    HourBlock readByBruno = blocks.findById(id).orElseThrow();

    readByAna.hold(UUID.randomUUID(), now);
    blocks.save(readByAna);

    readByBruno.hold(UUID.randomUUID(), now);
    assertThatThrownBy(() -> blocks.save(readByBruno))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  @DisplayName("open hours are the free and held ones from an instant on, only from their university")
  void openHoursAreTheFreeAndHeldOnes() {

    UUID student = UUID.randomUUID();

    HourBlock started = hour(tenant, now.minus(Duration.ofMinutes(30)));
    HourBlock free = hour(tenant, nineOClockPlus(0));
    HourBlock held = hour(tenant, nineOClockPlus(1));
    held.hold(student, now);
    HourBlock booked = hour(tenant, nineOClockPlus(2));
    booked.hold(student, now);
    booked.book(UUID.randomUUID(), student, now);
    HourBlock released = hour(tenant, nineOClockPlus(3));
    released.hold(student, now);
    released.book(UUID.randomUUID(), student, now);
    released.release();
    HourBlock elsewhere = hour(otherTenant, nineOClockPlus(4));

    blocks.saveAll(List.of(started, free, held, booked, released, elsewhere));

    AtomicReference<List<OpenHourView>> open = new AtomicReference<>();
    TenantContext.runAs(tenant, () -> open.set(bookingApi.openHoursOf(tutor, now)));

    assertThat(open.get())
        .containsExactly(
            new OpenHourView(free.getId(), tutor, nineOClockPlus(0), nineOClockPlus(1)),
            new OpenHourView(held.getId(), tutor, nineOClockPlus(1), nineOClockPlus(2)));
  }
}
