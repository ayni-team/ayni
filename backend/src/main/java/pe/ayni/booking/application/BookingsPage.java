package pe.ayni.booking.application;

import java.util.List;
import pe.ayni.booking.domain.model.BookingTimeline;

/**
 * A page of somebody's bookings, in one of the two halves they are read in.
 *
 * @param timezone the university's zone, in which the client shows the UTC instants
 * @param total how many bookings that half has in all
 */
public record BookingsPage<T>(
    BookingTimeline view, String timezone, int page, int size, long total, List<T> items) {}
