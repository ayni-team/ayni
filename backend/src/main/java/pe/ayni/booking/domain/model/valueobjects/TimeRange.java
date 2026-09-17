package pe.ayni.booking.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;

@Embeddable
public record TimeRange(
        @Column(name = "starts_at_time")
        LocalTime startsAtTime,

        @Column(name = "ends_at_time")
        LocalTime endsAtTime
) {
    public TimeRange {
        if (startsAtTime != null && endsAtTime != null && !endsAtTime.isAfter(startsAtTime)) {
            throw new IllegalArgumentException("endsAtTime must be strictly after startsAtTime");
        }
    }

    public static TimeRange of(LocalTime startsAtTime, LocalTime endsAtTime) {
        return new TimeRange(startsAtTime, endsAtTime);
    }
}