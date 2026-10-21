package pe.ayni.config.demo;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Reads and moves the clock of the {@code demo} profile.
 *
 * <p>Nothing is stored: the offset lives in memory and a restart takes the clock back to the real
 * time. The rules that read the clock decide what a move means; the scheduled jobs notice it on
 * their next run, within seconds or, for those that run at a time of day, within a minute.
 */
@Service
@Profile("demo")
public class DemoClockUseCase {

  private static final Logger log = LoggerFactory.getLogger(DemoClockUseCase.class);

  private final AdjustableClock clock;

  DemoClockUseCase(AdjustableClock clock) {
    this.clock = clock;
  }

  /** Where the clock is, where the real time is, and how far apart. */
  public DemoClockView read() {
    Duration offset = clock.offset();
    Instant real = clock.realInstant();
    return new DemoClockView(real.plus(offset), real, offset.toString());
  }

  /** Moves the clock by that much, forwards or backwards. */
  public DemoClockView advance(Duration by) {
    clock.advance(by);
    warnWhenMoved();
    return read();
  }

  /** Takes the clock to that instant, from which it keeps running. */
  public DemoClockView setTo(Instant at) {
    clock.setTo(at);
    warnWhenMoved();
    return read();
  }

  /** Takes the clock back to the real time. */
  public DemoClockView reset() {
    clock.reset();
    log.info("The demo clock reads the real time again");
    return read();
  }

  private void warnWhenMoved() {
    if (clock.isMoved()) {
      log.warn("The demo clock was moved: the application no longer reads the real time");
    }
  }
}
