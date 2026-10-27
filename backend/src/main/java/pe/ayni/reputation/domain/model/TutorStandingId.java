package pe.ayni.reputation.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite identifier for a tutor standing in one catalogue item and tenant.
 */
public class TutorStandingId implements Serializable {

    private String tenantId;
    private UUID tutorId;
    private UUID catalogItemId;

    public TutorStandingId() {
    }

    public TutorStandingId(String tenantId, UUID tutorId, UUID catalogItemId) {
        this.tenantId = tenantId;
        this.tutorId = tutorId;
        this.catalogItemId = catalogItemId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TutorStandingId that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(tutorId, that.tutorId)
                && Objects.equals(catalogItemId, that.catalogItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, tutorId, catalogItemId);
    }
}