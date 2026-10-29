package pe.ayni.booking.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.domain.model.Booking;

/**
 * The reservations.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface BookingRepository extends JpaRepository<Booking, UUID> {

  Optional<Booking> findByTenantIdAndId(String tenantId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select booking from Booking booking where booking.tenantId = :tenantId and booking.id = :id")
  Optional<Booking> lockByTenantIdAndId(
      @Param("tenantId") String tenantId, @Param("id") UUID id);

  /** A student's bookings still ahead: confirmed and not over, the nearest first (US04). */
  @Query(
      """
      select booking from Booking booking
      where booking.tenantId = :tenantId
        and booking.studentId = :studentId
        and booking.status = pe.ayni.booking.BookingStatus.CONFIRMED
        and booking.endsAt > :now
      order by booking.startsAt asc, booking.id asc
      """)
  Page<Booking> findUpcomingOfStudent(
      @Param("tenantId") String tenantId,
      @Param("studentId") UUID studentId,
      @Param("now") Instant now,
      Pageable page);

  /** The rest of a student's bookings: over, cancelled or not attended, the most recent first. */
  @Query(
      """
      select booking from Booking booking
      where booking.tenantId = :tenantId
        and booking.studentId = :studentId
        and (booking.status <> pe.ayni.booking.BookingStatus.CONFIRMED or booking.endsAt <= :now)
      order by booking.startsAt desc, booking.id desc
      """)
  Page<Booking> findHistoryOfStudent(
      @Param("tenantId") String tenantId,
      @Param("studentId") UUID studentId,
      @Param("now") Instant now,
      Pageable page);

  /** A tutor's confirmed bookings that are not over and touch the period, in order (US21). */
  @Query(
      """
      select booking from Booking booking
      where booking.tenantId = :tenantId
        and booking.tutorId = :tutorId
        and booking.status = pe.ayni.booking.BookingStatus.CONFIRMED
        and booking.startsAt < :periodEnd
        and booking.endsAt > :periodStart
        and booking.endsAt > :now
      order by booking.startsAt asc
      """)
  List<Booking> findUpcomingOfTutorWithin(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("periodStart") Instant periodStart,
      @Param("periodEnd") Instant periodEnd,
      @Param("now") Instant now);

  /** The sessions a tutor already had, or that were called off: the most recent first (US21). */
  @Query(
      """
      select booking from Booking booking
      where booking.tenantId = :tenantId
        and booking.tutorId = :tutorId
        and (booking.status <> pe.ayni.booking.BookingStatus.CONFIRMED or booking.endsAt <= :now)
      order by booking.startsAt desc, booking.id desc
      """)
  Page<Booking> findHistoryOfTutor(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("now") Instant now,
      Pageable page);

  /** Whether the university has a booking that starts before that instant (the demo history). */
  boolean existsByTenantIdAndStartsAtBefore(String tenantId, Instant instant);

  /** Whether the university has a booking in that state starting after that instant (demo data). */
  boolean existsByTenantIdAndStatusAndStartsAtAfter(
      String tenantId, BookingStatus status, Instant instant);

  /** Whether the university has any booking in that state (demo data). */
  boolean existsByTenantIdAndStatus(String tenantId, BookingStatus status);
}
