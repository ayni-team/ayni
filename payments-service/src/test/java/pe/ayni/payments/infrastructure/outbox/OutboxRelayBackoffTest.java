package pe.ayni.payments.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxRelayBackoffTest {

  private final OutboxRelay relay =
      new OutboxRelay(
          mock(OutboxRepository.class),
          mock(MonolithEvents.class),
          Clock.systemUTC(),
          Duration.ofSeconds(2),
          Duration.ofMinutes(5),
          20);

  @Test
  @DisplayName("The wait between attempts doubles up to its maximum")
  void theWaitDoublesUpToItsMaximum() {
    assertThat(relay.backoffAfter(1)).isEqualTo(Duration.ofSeconds(2));
    assertThat(relay.backoffAfter(2)).isEqualTo(Duration.ofSeconds(4));
    assertThat(relay.backoffAfter(3)).isEqualTo(Duration.ofSeconds(8));
    assertThat(relay.backoffAfter(4)).isEqualTo(Duration.ofSeconds(16));
    assertThat(relay.backoffAfter(8)).isEqualTo(Duration.ofSeconds(256));
    assertThat(relay.backoffAfter(9)).isEqualTo(Duration.ofMinutes(5));
    assertThat(relay.backoffAfter(1_000)).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void refusesAWaitThatCannotGrow() {
    assertThatThrownBy(
            () ->
                new OutboxRelay(
                    mock(OutboxRepository.class),
                    mock(MonolithEvents.class),
                    Clock.systemUTC(),
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(5),
                    20))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
