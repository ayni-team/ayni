package pe.ayni.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.domain.model.AgendaPeriod;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.BookingTimeline;
import pe.ayni.booking.domain.services.TutorAgenda;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.booking.infrastructure.StudentAbsenceRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * A tutor's agenda (US21): what is booked with them and what is still free in a period of days of
 * the university's zone, and apart from it the sessions they already had.
 *
 * <p>Only reads. The tutor is whoever asks, and only their bookings and hours of the current
 * university are read. The days of the period and the local times of each line are the
 * university's, never the server's.
 */
@Service
public class TutorAgendaQuery {

  private final BookingRepository bookings;
  private final HourBlockRepository blocks;
  private final StudentAbsenceRepository absences;
  private final BookingParties parties;
  private final IdentityApi identity;
  private final Clock clock;

  TutorAgendaQuery(
      BookingRepository bookings,
      HourBlockRepository blocks,
      StudentAbsenceRepository absences,
      BookingParties parties,
      IdentityApi identity,
      Clock clock) {
    this.bookings = bookings;
    this.blocks = blocks;
    this.absences = absences;
    this.parties = parties;
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * What is ahead from {@code from} to {@code to}, both days included: a missing {@code from} is
   * today in the university's zone and a missing {@code to} the end of a week from it.
   *
   * @throws pe.ayni.booking.domain.model.BookingRuleViolation when {@code to} is before {@code
   *     from} or the period is longer than {@link AgendaPeriod#MAX_DAYS} days
   */
  @Transactional(readOnly = true)
  public TutorAgendaView agenda(UUID tutorId, LocalDate from, LocalDate to) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    String tenantId = TenantContext.require();
    String timezone = identity.requireTenant(tenantId).timezone();
    ZoneId zone = ZoneId.of(timezone);
    Instant now = clock.instant();
    AgendaPeriod period = AgendaPeriod.requested(from, to, zone, now);

    List<TutorAgenda.Entry> entries =
        TutorAgenda.of(
            bookings.findUpcomingOfTutorWithin(
                tenantId, tutorId, period.start(), period.end(), now),
            blocks.findWithin(tenantId, tutorId, period.start(), period.end()),
            period,
            now);
    BookingParties.Names names = parties.forOneList();
    Map<UUID, Integer> absencesOf =
        absencesOf(
            tenantId,
            entries.stream()
                .filter(TutorAgenda.Booked.class::isInstance)
                .map(entry -> ((TutorAgenda.Booked) entry).booking())
                .toList());
    return new TutorAgendaView(
        period.from(),
        period.to(),
        timezone,
        entries.stream().map(entry -> lineOf(entry, zone, names, absencesOf, now)).toList());
  }

  /** The sessions the tutor already had, or that were called off, the most recent first. */
  @Transactional(readOnly = true)
  public BookingsPage<TutorBooking> history(UUID tutorId, int page, int size) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    String tenantId = TenantContext.require();
    Instant now = clock.instant();
    Page<Booking> found =
        bookings.findHistoryOfTutor(tenantId, tutorId, now, PageRequest.of(page, size));
    BookingParties.Names names = parties.forOneList();
    Map<UUID, Integer> absencesOf = absencesOf(tenantId, found.getContent());
    return new BookingsPage<>(
        BookingTimeline.HISTORY,
        identity.requireTenant(tenantId).timezone(),
        page,
        size,
        found.getTotalElements(),
        found.getContent().stream().map(booking -> bookingOf(booking, names, absencesOf, now)).toList());
  }

  /**
   * How many absences each student of these bookings has, in one query for the whole list. A student
   * with none is not in the map.
   */
  private Map<UUID, Integer> absencesOf(String tenantId, Collection<Booking> listed) {
    List<UUID> studentIds = listed.stream().map(Booking::getStudentId).distinct().toList();
    if (studentIds.isEmpty()) {
      return Map.of();
    }
    return absences.countByStudent(tenantId, studentIds).stream()
        .collect(
            Collectors.toMap(
                StudentAbsenceRepository.AbsencesOfStudent::getStudentId,
                found -> Math.toIntExact(found.getAbsences())));
  }

  private static TutorAgendaView.Line lineOf(
      TutorAgenda.Entry entry,
      ZoneId zone,
      BookingParties.Names names,
      Map<UUID, Integer> absencesOf,
      Instant now) {
    ZonedDateTime start = entry.startsAt().atZone(zone);
    ZonedDateTime end = entry.endsAt().atZone(zone);
    return switch (entry) {
      case TutorAgenda.Booked booked ->
          new TutorAgendaView.Line(
              TutorAgendaView.Kind.BOOKED,
              entry.startsAt(),
              entry.endsAt(),
              start.toLocalDate(),
              start.toLocalTime(),
              end.toLocalTime(),
              bookingOf(booked.booking(), names, absencesOf, now));
      case TutorAgenda.Free free ->
          new TutorAgendaView.Line(
              TutorAgendaView.Kind.FREE,
              entry.startsAt(),
              entry.endsAt(),
              start.toLocalDate(),
              start.toLocalTime(),
              end.toLocalTime(),
              null);
    };
  }

  private static TutorBooking bookingOf(
      Booking booking, BookingParties.Names names, Map<UUID, Integer> absencesOf, Instant now) {
    return new TutorBooking(
        booking.getId(),
        booking.getStartsAt(),
        booking.getEndsAt(),
        booking.getHours(),
        booking.getStatus(),
        booking.getStudentId(),
        names.personName(booking.getStudentId()),
        absencesOf.getOrDefault(booking.getStudentId(), 0),
        booking.getCatalogItemId(),
        names.itemName(booking.getCatalogItemId()),
        booking.getNeedDescription(),
        booking.isJoinableAt(now));
  }
}
