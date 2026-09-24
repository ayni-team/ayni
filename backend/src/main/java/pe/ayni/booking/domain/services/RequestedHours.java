package pe.ayni.booking.domain.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HoursNotOffered;

/**
 * The consecutive hours of one tutor a student asked for: a start and how many hours from it.
 *
 * <p>A booking covers consecutive hours, so the blocks found have to be exactly as many as asked
 * for, the first one has to start where the student said, and each one has to start where the one
 * before it ends. Consecutive is decided on instants, never on {@code LocalTime}: {@code
 * LocalTime.plusHours} wraps around midnight, and a stretch from 23:00 to 01:00 is still two
 * consecutive hours.
 *
 * <p>Whether each hour can be taken is not decided here but by the block itself, which knows who
 * holds it and until when.
 */
public final class RequestedHours {

  private final List<HourBlock> blocks;

  private RequestedHours(List<HourBlock> blocks) {
    this.blocks = blocks;
  }

  /**
   * Where the stretch asked for ends: the first moment after its last hour.
   *
   * <p>It is what the repository is asked for, half open, so that the blocks of the stretch come
   * back in one query instead of one per hour.
   *
   * @throws BookingRuleViolation when no hours are asked for
   */
  public static Instant endOf(Instant start, int hours) {
    Objects.requireNonNull(start, "start must not be null");
    requireSomeHours(hours);
    return start.plus(Duration.ofHours(hours));
  }

  /**
   * Checks that the blocks found cover the stretch asked for, and nothing else.
   *
   * @param found the tutor's blocks from {@code start} up to {@link #endOf(Instant, int)}
   * @throws BookingRuleViolation when no hours are asked for
   * @throws HoursNotOffered when the tutor does not offer that many consecutive hours from there
   */
  public static RequestedHours of(List<HourBlock> found, Instant start, int hours) {
    Objects.requireNonNull(found, "found must not be null");
    Objects.requireNonNull(start, "start must not be null");
    requireSomeHours(hours);

    List<HourBlock> sorted =
        found.stream().sorted(Comparator.comparing(HourBlock::getStartsAt)).toList();

    if (sorted.size() != hours || !sorted.getFirst().getStartsAt().equals(start)) {
      throw notOffered(start, hours);
    }
    for (int i = 1; i < sorted.size(); i++) {
      if (!sorted.get(i).getStartsAt().equals(sorted.get(i - 1).getEndsAt())) {
        throw notOffered(start, hours);
      }
    }
    return new RequestedHours(sorted);
  }

  private static void requireSomeHours(int hours) {
    if (hours < 1) {
      throw new BookingRuleViolation("A booking covers at least one hour");
    }
  }

  private static HoursNotOffered notOffered(Instant start, int hours) {
    return new HoursNotOffered(
        hours == 1
            ? "The tutor does not offer the hour starting at " + start
            : "The tutor does not offer " + hours + " consecutive hours starting at " + start);
  }

  /** The blocks, in the order they happen. */
  public List<HourBlock> blocks() {
    return blocks;
  }

  public List<UUID> blockIds() {
    return blocks.stream().map(HourBlock::getId).toList();
  }

  public Instant startsAt() {
    return blocks.getFirst().getStartsAt();
  }

  public Instant endsAt() {
    return blocks.getLast().getEndsAt();
  }

  public int hours() {
    return blocks.size();
  }
}
