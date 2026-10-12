package pe.ayni.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.BookingCancellationConflict;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;
import pe.ayni.booking.domain.model.NotBookingParticipant;
import pe.ayni.booking.domain.model.ReliabilityIncidentKind;
import pe.ayni.booking.domain.model.TutorReliabilityIncident;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.booking.infrastructure.TutorReliabilityIncidentRepository;
import pe.ayni.shared.events.BookingCancelled;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;

class CancelBookingUseCaseTest {

  private static final String UPC = "UPC";
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

  private final BookingRepository bookings = mock(BookingRepository.class);
  private final HourBlockRepository blocks = mock(HourBlockRepository.class);
  private final TutorReliabilityIncidentRepository incidents =
      mock(TutorReliabilityIncidentRepository.class);
  private final WalletApi wallet = mock(WalletApi.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final CancelBookingUseCase useCase =
      new CancelBookingUseCase(
          bookings,
          blocks,
          incidents,
          wallet,
          events,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("a student cancellation at least twelve hours ahead refunds and reopens the hour")
  void refundsAndReturnsTheHourForAnOnTimeStudentCancellation() {
    Booking booking = booking(NOW.plus(Duration.ofHours(13)));
    HourBlock block = bookedBlock(booking);
    arrange(booking, block);

    Optional<LateCancellationConfirmation> result =
        asUpc(() -> useCase.execute(STUDENT, booking.getId(), false));

    assertThat(result).isEmpty();
    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    assertThat(booking.getCancelledBy().name()).isEqualTo("STUDENT");
    assertThat(booking.getCancelledLate()).isFalse();
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    assertThat(block.getBookingId()).isNull();
    verify(wallet).refund(booking.getId());
    verify(incidents, never()).save(any(TutorReliabilityIncident.class));
    assertCancellationEvent(booking, block, BookingCancelled.CancelledBy.STUDENT, false);
  }

  @Test
  @DisplayName("exactly twelve hours before the scheduled start is not a late cancellation")
  void twelveHourBoundaryIsRefundable() {
    Booking booking = booking(NOW.plus(Duration.ofHours(12)));
    HourBlock block = bookedBlock(booking);
    arrange(booking, block);

    asUpc(() -> useCase.execute(STUDENT, booking.getId(), false));

    assertThat(booking.getCancelledLate()).isFalse();
    verify(wallet).refund(booking.getId());
    assertCancellationEvent(booking, block, BookingCancelled.CancelledBy.STUDENT, false);
  }

  @Test
  @DisplayName("a late cancellation first returns a warning without changing booking state")
  void lateCancellationNeedsConfirmationBeforeAnyChanges() {
    Booking booking = booking(NOW.plus(Duration.ofHours(11)).plusSeconds(59));
    HourBlock block = bookedBlock(booking);
    arrange(booking, block);

    Optional<LateCancellationConfirmation> result =
        asUpc(() -> useCase.execute(STUDENT, booking.getId(), false));

    assertThat(result)
        .contains(
            new LateCancellationConfirmation(
                booking.getId(), booking.getStartsAt(), false, false));
    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.BOOKED);
    verifyNoInteractions(wallet, incidents, events);
    verify(blocks, never()).findByTenantIdAndBookingIdOrderByStartsAtAsc(any(), any());
  }

  @Test
  @DisplayName("a confirmed late student cancellation returns the hour without a refund")
  void confirmedLateStudentCancellationDoesNotRefund() {
    Booking booking = booking(NOW.plus(Duration.ofHours(11)));
    HourBlock block = bookedBlock(booking);
    arrange(booking, block);

    asUpc(() -> useCase.execute(STUDENT, booking.getId(), true));

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    assertThat(booking.getCancelledLate()).isTrue();
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.AVAILABLE);
    verifyNoInteractions(wallet, incidents);
    assertCancellationEvent(booking, block, BookingCancelled.CancelledBy.STUDENT, true);
  }

  @Test
  @DisplayName("a tutor cancellation ahead of time refunds the student and releases the hour")
  void tutorCancellationAheadOfTimeRefundsAndReleasesTheHour() {
    Booking booking = booking(NOW.plus(Duration.ofHours(13)));
    HourBlock block = bookedBlock(booking);
    arrange(booking, block);

    Optional<LateCancellationConfirmation> result =
        asUpc(() -> useCase.execute(TUTOR, booking.getId(), false));

    assertThat(result).isEmpty();
    assertThat(booking.getCancelledBy().name()).isEqualTo("TUTOR");
    assertThat(booking.getCancelledLate()).isFalse();
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.RELEASED);
    assertThat(block.getBookingId()).isNull();
    verify(wallet).refund(booking.getId());
    verify(incidents, never()).save(any(TutorReliabilityIncident.class));
    assertCancellationEvent(booking, block, BookingCancelled.CancelledBy.TUTOR, false);
  }

  @Test
  @DisplayName("a late tutor cancellation first warns that it will be recorded and refunds")
  void lateTutorCancellationNeedsConfirmationAndWarnsAboutTheRecord() {
    Booking booking = booking(NOW.plus(Duration.ofHours(11)));
    HourBlock block = bookedBlock(booking);
    arrange(booking, block);

    Optional<LateCancellationConfirmation> result =
        asUpc(() -> useCase.execute(TUTOR, booking.getId(), false));

    assertThat(result)
        .contains(
            new LateCancellationConfirmation(
                booking.getId(), booking.getStartsAt(), true, true));
    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.BOOKED);
    verifyNoInteractions(wallet, incidents, events);
  }

  @Test
  @DisplayName("a confirmed late tutor cancellation refunds, releases the hour and is recorded")
  void confirmedLateTutorCancellationRefundsAndRecordsAnIncident() {
    Booking booking = booking(NOW.plus(Duration.ofHours(11)));
    HourBlock block = bookedBlock(booking);
    arrange(booking, block);

    asUpc(() -> useCase.execute(TUTOR, booking.getId(), true));

    assertThat(booking.getCancelledBy().name()).isEqualTo("TUTOR");
    assertThat(booking.getCancelledLate()).isTrue();
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.RELEASED);
    assertThat(block.getBookingId()).isNull();
    verify(wallet).refund(booking.getId());
    ArgumentCaptor<TutorReliabilityIncident> incident =
        ArgumentCaptor.forClass(TutorReliabilityIncident.class);
    verify(incidents).save(incident.capture());
    assertThat(incident.getValue()).extracting("kind").isEqualTo(ReliabilityIncidentKind.LATE_CANCELLATION);
    assertThat(incident.getValue()).extracting("tutorId").isEqualTo(TUTOR);
    assertThat(incident.getValue()).extracting("bookingId").isEqualTo(booking.getId());
    assertCancellationEvent(booking, block, BookingCancelled.CancelledBy.TUTOR, true);
  }

  @Test
  @DisplayName("a booking cannot be cancelled at or after its scheduled start")
  void refusesCancellationAtTheScheduledStart() {
    Booking booking = booking(NOW);
    when(bookings.lockByTenantIdAndId(UPC, booking.getId())).thenReturn(Optional.of(booking));

    assertThatThrownBy(() -> asUpc(() -> useCase.execute(STUDENT, booking.getId(), true)))
        .isInstanceOf(BookingCancellationConflict.class)
        .hasMessageContaining("after it has started");

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    verifyNoInteractions(wallet, incidents, events);
    verify(blocks, never()).findByTenantIdAndBookingIdOrderByStartsAtAsc(any(), any());
  }

  @Test
  @DisplayName("only the student or tutor of the booking may cancel it")
  void rejectsANonParticipant() {
    Booking booking = booking(NOW.plus(Duration.ofDays(1)));
    arrange(booking, bookedBlock(booking));

    assertThatThrownBy(() -> asUpc(() -> useCase.execute(UUID.randomUUID(), booking.getId(), false)))
        .isInstanceOf(NotBookingParticipant.class);

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    verifyNoInteractions(wallet, incidents, events);
  }

  @Test
  @DisplayName("a booking that does not exist in this university is not found")
  void aMissingBookingIsNotFound() {
    UUID missing = UUID.randomUUID();
    when(bookings.lockByTenantIdAndId(UPC, missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> asUpc(() -> useCase.execute(STUDENT, missing, false)))
        .isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(wallet, incidents, events);
  }

  @Test
  @DisplayName("a confirmed booking with no hour blocks is not partially cancelled")
  void refusesToCancelWhenThereAreNoBookedBlocks() {
    Booking booking = booking(NOW.plus(Duration.ofDays(1)));
    when(bookings.lockByTenantIdAndId(UPC, booking.getId())).thenReturn(Optional.of(booking));
    when(blocks.findByTenantIdAndBookingIdOrderByStartsAtAsc(UPC, booking.getId()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> asUpc(() -> useCase.execute(STUDENT, booking.getId(), false)))
        .isInstanceOf(BookingRuleViolation.class)
        .hasMessageContaining("no booked hour blocks");

    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    verifyNoInteractions(wallet, incidents, events);
  }

  @Test
  @DisplayName("repeating cancellation of a cancelled booking does not refund or publish again")
  void repeatedCancellationIsIdempotent() {
    Booking booking = booking(NOW.plus(Duration.ofDays(1)));
    booking.cancel(
        pe.ayni.booking.domain.model.CancelledBy.STUDENT, "Cancelled by participant", NOW);
    when(bookings.lockByTenantIdAndId(UPC, booking.getId())).thenReturn(Optional.of(booking));

    assertThat(asUpc(() -> useCase.execute(STUDENT, booking.getId(), false))).isEmpty();

    verifyNoInteractions(blocks, wallet, incidents, events);
  }

  private void arrange(Booking booking, HourBlock block) {
    when(bookings.lockByTenantIdAndId(UPC, booking.getId())).thenReturn(Optional.of(booking));
    when(blocks.findByTenantIdAndBookingIdOrderByStartsAtAsc(UPC, booking.getId()))
        .thenReturn(List.of(block));
  }

  private void assertCancellationEvent(
      Booking booking, HourBlock block, BookingCancelled.CancelledBy actor, boolean late) {
    ArgumentCaptor<BookingCancelled> event = ArgumentCaptor.forClass(BookingCancelled.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().tenantId()).isEqualTo(UPC);
    assertThat(event.getValue().bookingId()).isEqualTo(booking.getId());
    assertThat(event.getValue().studentId()).isEqualTo(STUDENT);
    assertThat(event.getValue().tutorId()).isEqualTo(TUTOR);
    assertThat(event.getValue().releasedBlockIds()).containsExactly(block.getId());
    assertThat(event.getValue().cancelledBy()).isEqualTo(actor);
    assertThat(event.getValue().late()).isEqualTo(late);
    assertThat(event.getValue().occurredOn()).isEqualTo(NOW);
  }

  private static Booking booking(Instant startsAt) {
    return Booking.confirm(
        UUID.randomUUID(),
        UPC,
        STUDENT,
        TUTOR,
        UUID.randomUUID(),
        startsAt,
        startsAt.plus(Duration.ofHours(1)),
        1,
        "Help with calculus",
        NOW.minus(Duration.ofDays(1)));
  }

  private static HourBlock bookedBlock(Booking booking) {
    HourBlock block =
        new HourBlock(
            UUID.randomUUID(),
            UPC,
            TUTOR,
            booking.getStartsAt(),
            booking.getEndsAt(),
            null,
            NOW.minus(Duration.ofDays(1)));
    block.hold(STUDENT, NOW);
    block.book(booking.getId(), STUDENT, NOW);
    return block;
  }

  private static <T> T asUpc(Supplier<T> work) {
    java.util.concurrent.atomic.AtomicReference<T> result =
        new java.util.concurrent.atomic.AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(work.get()));
    return result.get();
  }
}
