package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import pe.ayni.booking.application.BookingsPage;
import pe.ayni.booking.application.TutorBooking;
import pe.ayni.booking.domain.model.BookingTimeline;

/** A page of the sessions a tutor already had (US21). */
@Schema(name = "TutorBookings")
public record TutorBookingsResponse(
    @Schema(description = "Always HISTORY: the most recent first.", example = "HISTORY")
    BookingTimeline view,
    @Schema(description = "Time zone of the university, to show the UTC instants in.",
        example = "America/Lima")
    String timezone,
    @Schema(description = "Page number, from 0.", example = "0") int page,
    @Schema(description = "Page size.", example = "20") int size,
    @Schema(description = "Bookings of the history in all.", example = "12") long total,
    List<TutorAgendaResponse.Booked> items) {

  static TutorBookingsResponse of(BookingsPage<TutorBooking> page) {
    return new TutorBookingsResponse(
        page.view(),
        page.timezone(),
        page.page(),
        page.size(),
        page.total(),
        page.items().stream().map(TutorAgendaResponse.Booked::of).toList());
  }
}
