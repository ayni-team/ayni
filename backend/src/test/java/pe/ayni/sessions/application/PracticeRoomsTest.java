package pe.ayni.sessions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import pe.ayni.identity.IdentityApi;
import pe.ayni.sessions.domain.model.PracticeRoomsFull;
import pe.ayni.shared.tenancy.TenantContext;

/** Keeping the practice rooms: the switch, the limit, the lifetime and the university. */
class PracticeRoomsTest {

  private static final Instant OPENED = Instant.parse("2026-11-10T15:00:00Z");

  /** A clock the test moves. */
  private static final class MovableClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(OPENED);

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }

    void advance(Duration by) {
      now.updateAndGet(instant -> instant.plus(by));
    }
  }

  private final MovableClock clock = new MovableClock();
  private final IdentityApi identity = mock(IdentityApi.class);
  private final WhiteboardEditing editing = mock(WhiteboardEditing.class);

  @SuppressWarnings("unchecked")
  private PracticeRooms rooms(boolean enabled, int maxOpen) {
    ObjectProvider<WhiteboardEditing> provider = mock(ObjectProvider.class);
    when(provider.getObject()).thenReturn(editing);
    when(identity.namesOf(any())).thenReturn(Map.of());
    VideoRoomPasses noVideo = (room, user, role, now, notAfter) -> Optional.empty();
    return new PracticeRooms(
        enabled, maxOpen, Duration.ofHours(4), clock, noVideo, identity, provider);
  }

  @Test
  void offNothingOpensAndNoIdIsARoom() {
    PracticeRooms off = rooms(false, 20);
    assertThatThrownBy(() -> TenantContext.callAs("UPC", off::open))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessage("Practice rooms are not enabled");
    assertThat(TenantContext.callAs("UPC", () -> off.isOpen(UUID.randomUUID()))).isFalse();
  }

  @Test
  void oneRoomMoreThanTheLimitIsRefusedUntilOneIsForgotten() {
    PracticeRooms on = rooms(true, 2);
    TenantContext.callAs("UPC", on::open);
    clock.advance(Duration.ofHours(1));
    TenantContext.callAs("UPC", on::open);

    assertThatThrownBy(() -> TenantContext.callAs("UPC", on::open))
        .isInstanceOf(PracticeRoomsFull.class);

    // The first one's four hours are over: it is forgotten, and there is room for another.
    clock.advance(Duration.ofHours(3));
    UUID third = TenantContext.callAs("UPC", on::open);
    assertThat(TenantContext.callAs("UPC", () -> on.isOpen(third))).isTrue();
    verify(editing).closeLive(any());
  }

  @Test
  void aRoomIsForgottenWhenItsLifetimeIsOver() {
    PracticeRooms on = rooms(true, 20);
    UUID id = TenantContext.callAs("UPC", on::open);
    UUID ana = UUID.randomUUID();
    TenantContext.callAs("UPC", () -> on.join(id, ana));

    clock.advance(Duration.ofHours(4));
    on.forgetExpired();

    assertThat(TenantContext.callAs("UPC", () -> on.isOpen(id))).isFalse();
    assertThatThrownBy(() -> TenantContext.callAs("UPC", () -> on.details(id, ana)))
        .isInstanceOf(NoSuchElementException.class);
    verify(editing).closeLive(id);
  }

  @Test
  void aRoomIsOnlyARoomForItsOwnUniversity() {
    PracticeRooms on = rooms(true, 20);
    UUID id = TenantContext.callAs("UPC", on::open);

    assertThat(TenantContext.callAs("UPC", () -> on.isOpen(id))).isTrue();
    assertThat(TenantContext.callAs("USIL", () -> on.isOpen(id))).isFalse();
    assertThatThrownBy(() -> TenantContext.callAs("USIL", () -> on.join(id, UUID.randomUUID())))
        .isInstanceOf(NoSuchElementException.class);
  }
}
