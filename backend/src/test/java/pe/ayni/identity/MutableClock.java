package pe.ayni.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock a test can move: ten minutes later, the access link has expired.
 *
 * <p>The same idea as booking's, kept here because a module's tests do not reach into another
 * module's test package.
 */
final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    MutableClock(Instant start) {
        this(new AtomicReference<>(start), ZoneOffset.UTC);
    }

    private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    void set(Instant instant) {
        now.set(instant);
    }

    void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now, zone);
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
