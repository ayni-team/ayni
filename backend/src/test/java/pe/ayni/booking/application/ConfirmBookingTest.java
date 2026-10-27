package pe.ayni.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.HoldExpired;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;
import pe.ayni.booking.domain.model.HoursNotOffered;
import pe.ayni.booking.domain.model.TutorNotBookable;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.SkillsApi;
import pe.ayni.wallet.InsufficientCreditsException;
import pe.ayni.wallet.WalletApi;

/**
 * The order of the confirmation is the rule: nothing is charged before the tutor and the hours are
 * checked, and nothing is published before the booking is saved.
 */
class ConfirmBookingTest {

  private static final String UPC = "UPC";
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID SUBJECT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
  private static final Instant NINE = NOW.plus(Duration.ofDays(1));

  private final HourBlockRepository blocks = mock(HourBlockRepository.class);
  private final BookingRepository bookings = mock(BookingRepository.class);
  private final IdentityApi identity = mock(IdentityApi.class);
  private final SkillsApi skills = mock(SkillsApi.class);
  private final WalletApi wallet = mock(WalletApi.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

  private final ConfirmBooking confirmBooking =
      new ConfirmBooking(
          blocks, bookings, identity, skills, wallet, events, Clock.fixed(NOW, ZoneOffset.UTC));

  private final HourBlock nine = hour(NINE);
  private final HourBlock ten = hour(NINE.plus(Duration.ofHours(1)));

  private static HourBlock hour(Instant start) {
    return new HourBlock(
        UUID.randomUUID(), UPC, TUTOR, start, start.plus(Duration.ofHours(1)), null, NOW);
  }

  @BeforeEach
  void anEnabledTutorAndTwoHeldHours() {
    when(identity.isActive(any())).thenReturn(true);
    when(skills.isTutorEnabledFor(TUTOR, SUBJECT)).thenReturn(true);
    when(blocks.findWithin(UPC, TUTOR, NINE, NINE.plus(Duration.ofHours(2))))
        .thenReturn(List.of(nine, ten));
    nine.hold(STUDENT, NOW.minus(Duration.ofMinutes(1)));
    ten.hold(STUDENT, NOW.minus(Duration.ofMinutes(1)));
  }

  private <T> T asUpc(Supplier<T> work) {
    TenantContext.set(UPC);
    try {
      return work.get();
    } finally {
      TenantContext.clear();
    }
  }

  private ConfirmedBooking confirmTwoHours() {
    return asUpc(() -> confirmBooking.confirm(STUDENT, TUTOR, SUBJECT, NINE, 2, "Normal forms"));
  }

  @Test
  @DisplayName("the steps happen in the contract's order: tutor, hours, charge, mark, save, publish")
  void followsTheContractOrder() {

    ConfirmedBooking confirmed = confirmTwoHours();

    InOrder order = inOrder(skills, blocks, wallet, bookings, events);
    order.verify(skills).isTutorEnabledFor(TUTOR, SUBJECT);
    order.verify(blocks).findWithin(any(), any(), any(), any());
    order.verify(wallet).charge(STUDENT, Credits.of(2), confirmed.id());
    order.verify(blocks).flush();
    order.verify(bookings).save(any(Booking.class));
    order.verify(events).publishEvent(any(BookingConfirmed.class));

    assertThat(nine.getStatus()).isEqualTo(HourBlockStatus.BOOKED);
    assertThat(ten.getBookingId()).isEqualTo(confirmed.id());
    assertThat(confirmed.creditsCharged()).isEqualTo(2);
  }

  @Test
  @DisplayName("BookingConfirmed carries the university, the blocks and what was charged")
  void announcesTheBooking() {

    ConfirmedBooking confirmed = confirmTwoHours();

    ArgumentCaptor<BookingConfirmed> published = ArgumentCaptor.forClass(BookingConfirmed.class);
    verify(events).publishEvent(published.capture());
    BookingConfirmed event = published.getValue();
    assertThat(event.tenantId()).isEqualTo(UPC);
    assertThat(event.bookingId()).isEqualTo(confirmed.id());
    assertThat(event.studentId()).isEqualTo(STUDENT);
    assertThat(event.tutorId()).isEqualTo(TUTOR);
    assertThat(event.catalogItemId()).isEqualTo(SUBJECT);
    assertThat(event.startsAt()).isEqualTo(NINE);
    assertThat(event.endsAt()).isEqualTo(NINE.plus(Duration.ofHours(2)));
    assertThat(event.blockIds()).containsExactly(nine.getId(), ten.getId());
    assertThat(event.creditsCharged()).isEqualTo(Credits.of(2));
    assertThat(event.occurredOn()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("a tutor not enabled for the subject is refused before anything is charged")
  void refusesATutorWhoIsNotEnabled() {

    when(skills.isTutorEnabledFor(TUTOR, SUBJECT)).thenReturn(false);

    assertThatThrownBy(this::confirmTwoHours).isInstanceOf(TutorNotBookable.class);
    verifyNoInteractions(wallet, bookings, events);
  }

  @Test
  @DisplayName("a tutor who is no longer active is refused before anything is charged")
  void refusesAnInactiveTutor() {

    when(identity.isActive(TUTOR)).thenReturn(false);

    assertThatThrownBy(this::confirmTwoHours).isInstanceOf(TutorNotBookable.class);
    verifyNoInteractions(wallet);
  }

  @Test
  @DisplayName("hours that are not consecutive are refused before anything is charged")
  void refusesHoursThatAreNotConsecutive() {

    when(blocks.findWithin(UPC, TUTOR, NINE, NINE.plus(Duration.ofHours(2))))
        .thenReturn(List.of(nine));

    assertThatThrownBy(this::confirmTwoHours).isInstanceOf(HoursNotOffered.class);
    verifyNoInteractions(wallet);
  }

  @Test
  @DisplayName("an hour whose hold ran out is refused before anything is charged")
  void refusesAnExpiredHold() {

    ten.releaseHoldOf(STUDENT);

    assertThatThrownBy(this::confirmTwoHours).isInstanceOf(HoldExpired.class);
    verify(wallet, never()).charge(any(), any(), any());
    assertThat(nine.getStatus()).isEqualTo(HourBlockStatus.HELD);
  }

  @Test
  @DisplayName("when the charge is refused, no hour is marked and nothing is saved")
  void aRefusedChargeLeavesTheHoursAlone() {

    when(wallet.charge(eq(STUDENT), any(), any()))
        .thenThrow(new InsufficientCreditsException(Credits.of(1)));

    assertThatThrownBy(this::confirmTwoHours)
        .isInstanceOf(InsufficientCreditsException.class);
    assertThat(nine.getStatus()).isEqualTo(HourBlockStatus.HELD);
    verify(blocks, never()).flush();
    verifyNoInteractions(bookings, events);
  }
}
