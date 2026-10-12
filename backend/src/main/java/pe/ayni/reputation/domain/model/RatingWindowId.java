package pe.ayni.reputation.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class RatingWindowId implements Serializable {

    private String tenantId;
    private UUID sessionId;

    public RatingWindowId() {}

    public RatingWindowId(String tenantId, UUID sessionId) {
        this.tenantId = tenantId;
        this.sessionId = sessionId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof RatingWindowId other)) {
            return false;
        }

        return Objects.equals(tenantId, other.tenantId)
                && Objects.equals(sessionId, other.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, sessionId);
    }
}