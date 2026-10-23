package pe.ayni.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.BookingTimeline;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * A student's tutorings (US04): the ones still ahead, the nearest first, or the history apart from
 * them, the most recent first, one page at a time.
 *
 * <p>Only reads. The student is whoever asks, and only their bookings of the current university are
 * read. The tutor's name and the item's come from identity and skills; the instants are UTC and the
 * answer carries the university's zone to show them in.
 */
@Service
public class StudentBookingsQuery {

  private final BookingRepository bookings;
  private final BookingParties parties;
  private final IdentityApi identity;
  private final Clock clock;

  StudentBookingsQuery(
      BookingRepository bookings, BookingParties parties, IdentityApi identity, Clock clock) {
    this.bookings = bookings;
    this.parties = parties;
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * @param view {@code UPCOMING} (also when absent) or {@code HISTORY}, in any case
   * @throws pe.ayni.booking.domain.model.BookingRuleViolation when the view is neither
   */
  @Transactional(readOnly = true)
  public BookingsPage<StudentBooking> list(UUID studentId, String view, int page, int size) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    BookingTimeline timeline = BookingTimeline.named(view);
    String tenantId = TenantContext.require();
    Instant now = clock.instant();
    PageRequest request = PageRequest.of(page, size);
    Page<Booking> found =
        timeline == BookingTimeline.UPCOMING
            ? bookings.findUpcomingOfStudent(tenantId, studentId, now, request)
            : bookings.findHistoryOfStudent(tenantId, studentId, now, request);
    BookingParties.Names names = parties.forList(found.getContent(), Booking::getTutorId);
    return new BookingsPage<>(
        timeline,
        identity.requireTenant(tenantId).timezone(),
        page,
        size,
        found.getTotalElements(),
        found.getContent().stream()
            .map(
                booking ->
                    new StudentBooking(
                        booking.getId(),
                        booking.getStartsAt(),
                        booking.getEndsAt(),
                        booking.getHours(),
                        booking.getStatus(),
                        booking.getTutorId(),
                        names.personName(booking.getTutorId()),
                        booking.getCatalogItemId(),
                        names.itemName(booking.getCatalogItemId()),
                        booking.isJoinableAt(now)))
            .toList());
  }
}
