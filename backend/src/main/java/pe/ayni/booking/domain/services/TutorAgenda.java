package pe.ayni.booking.domain.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import pe.ayni.booking.domain.model.AgendaPeriod;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.HourBlock;

/**
 * What a tutor has ahead in a period (US21): the sessions booked with them and the hours still
 * free, in the order they come.
 *
 * <p>A booking appears once, however many hours it covers, from as soon as it is confirmed until
 * its last hour ends; its hours are not listed again as blocks. An hour is free while nobody booked
 * it and it has not started, since an hour that started can no longer be booked. Hours that were
 * withdrawn or released are neither, and are not shown.
 */
public final class TutorAgenda {

  private TutorAgenda() {}

  /** One line of the agenda. */
  public sealed interface Entry permits Booked, Free {
    Instant startsAt();

    Instant endsAt();
  }

  /** A session booked with the tutor. */
  public record Booked(Booking booking) implements Entry {
    @Override
    public Instant startsAt() {
      return booking.getStartsAt();
    }

    @Override
    public Instant endsAt() {
      return booking.getEndsAt();
    }
  }

  /** An hour nobody booked yet. */
  public record Free(UUID blockId, Instant startsAt, Instant endsAt) implements Entry {}

  /**
   * The agenda of the period at that moment.
   *
   * @param bookings the tutor's bookings that may fall in the period
   * @param blocks the tutor's hours that may fall in the period
   */
  public static List<Entry> of(
      List<Booking> bookings, List<HourBlock> blocks, AgendaPeriod period, Instant now) {
    Objects.requireNonNull(period, "period must not be null");
    Objects.requireNonNull(now, "now must not be null");
    List<Entry> entries = new ArrayList<>();
    for (Booking booking : bookings) {
      if (booking.isUpcomingAt(now) && overlaps(booking.getStartsAt(), booking.getEndsAt(), period)) {
        entries.add(new Booked(booking));
      }
    }
    for (HourBlock block : blocks) {
      if (block.isFreeAt(now) && overlaps(block.getStartsAt(), block.getEndsAt(), period)) {
        entries.add(new Free(block.getId(), block.getStartsAt(), block.getEndsAt()));
      }
    }
    entries.sort(Comparator.comparing(Entry::startsAt));
    return List.copyOf(entries);
  }

  private static boolean overlaps(Instant start, Instant end, AgendaPeriod period) {
    return start.isBefore(period.end()) && end.isAfter(period.start());
  }
}
